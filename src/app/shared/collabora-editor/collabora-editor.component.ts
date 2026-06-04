import {
  Component, ElementRef, OnDestroy, OnInit, ViewChild,
  inject, input, output, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

export interface WopiSession {
  collaboraUrl: string;
  accessToken: string;
  accessTokenTtl: number;
  canWrite: boolean;
}

/**
 * Composant d'édition documentaire via Collabora Online (WOPI).
 *
 * Charge Collabora via [src] sur l'iframe — le access_token est passé
 * directement dans l'URL query string (méthode GET WOPI standard).
 *
 * Usage :
 *   <app-collabora-editor [bureauDocumentId]="doc.id" context="BUREAU"
 *     (documentSaved)="onSaved()" (documentClosed)="onClosed()" />
 */
@Component({
  selector: 'app-collabora-editor',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="relative w-full h-full flex flex-col">

      <!-- Loader -->
      @if (loading()) {
        <div class="absolute inset-0 z-10 flex items-center justify-center bg-slate-50">
          <div class="flex flex-col items-center gap-3">
            <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
            <span class="text-slate-500 text-sm">Chargement de l'éditeur sécurisé…</span>
          </div>
        </div>
      }

      <!-- iframe Collabora — src défini uniquement quand la session WOPI est prête -->
      @if (iframeSrc()) {
        <iframe #collaboraFrame
                [src]="iframeSrc()!"
                class="w-full flex-1 border-0"
                style="min-height: 600px;"
                allow="fullscreen"
                (load)="onFrameLoad()">
        </iframe>
      }
    </div>
  `
})
export class CollaboraEditorComponent implements OnInit, OnDestroy {

  // ── Inputs ────────────────────────────────────────────────────────────────
  bureauDocumentId = input.required<string>();
  context          = input.required<'BUREAU' | 'PARAPHEUR'>();

  // ── Outputs ───────────────────────────────────────────────────────────────
  documentSaved  = output<void>();
  documentClosed = output<void>();
  documentLoaded = output<void>();

  // ── State ─────────────────────────────────────────────────────────────────
  loading   = signal(true);
  iframeSrc = signal<SafeResourceUrl | null>(null);
  canWrite  = signal(false);

  @ViewChild('collaboraFrame') frame!: ElementRef<HTMLIFrameElement>;

  private api       = inject(ApiService);
  private sanitizer = inject(DomSanitizer);
  private toast     = inject(ToastService);

  private messageHandler = (ev: MessageEvent) => this.onCollaboraMessage(ev);

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    window.addEventListener('message', this.messageHandler);
    this.initSession();
  }

  ngOnDestroy() {
    window.removeEventListener('message', this.messageHandler);
  }

  // ── Session WOPI ─────────────────────────────────────────────────────────

  private initSession() {
    this.loading.set(true);
    const call = this.context() === 'BUREAU'
      ? this.api.openBureauWopiSession(this.bureauDocumentId())
      : this.api.openParapheurWopiSession(this.bureauDocumentId());

    call.subscribe({
      next: (s: WopiSession) => {
        this.canWrite.set(s.canWrite);
        // Construire l'URL complète avec access_token dans le query string
        const fullUrl = `${s.collaboraUrl}&access_token=${encodeURIComponent(s.accessToken)}&access_token_ttl=${s.accessTokenTtl}`;
        this.iframeSrc.set(
          this.sanitizer.bypassSecurityTrustResourceUrl(fullUrl)
        );
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Impossible d\'ouvrir l\'éditeur de document');
      }
    });
  }

  onFrameLoad() {
    // L'iframe a fini de charger (peut être la page d'accueil Collabora ou le doc)
  }

  // ── PostMessage Collabora ─────────────────────────────────────────────────

  private onCollaboraMessage(ev: MessageEvent) {
    if (!ev.data || typeof ev.data !== 'string') return;
    let msg: { MessageId: string; Values?: Record<string, unknown> };
    try { msg = JSON.parse(ev.data); } catch { return; }

    switch (msg.MessageId) {
      case 'App_LoadingStatus':
        if (msg.Values?.['Status'] === 'Document_Loaded') {
          this.postToFrame({ MessageId: 'Host_PostmessageReady' });
          this.documentLoaded.emit();
        }
        break;
      case 'Action_Save_Resp':
        if (msg.Values?.['success'] === true || msg.Values?.['Modified'] === false) {
          this.documentSaved.emit();
        }
        break;
      case 'UI_Close':
        this.documentClosed.emit();
        break;
    }
  }

  // ── API publique ──────────────────────────────────────────────────────────

  forceSave() {
    this.postToFrame({ MessageId: 'Action_Save', Values: { DontTerminateEdit: false } });
  }

  injectSignature(signetName: string, signatureBase64: string) {
    this.postToFrame({
      MessageId: 'uno',
      SendTime: Date.now(),
      Command: '.uno:JumpToBookmark',
      Args: { BookmarkName: signetName }
    });
    setTimeout(() => {
      this.postToFrame({
        MessageId: 'uno',
        SendTime: Date.now(),
        Command: '.uno:InsertGraphic',
        Args: { FileName: `data:image/png;base64,${signatureBase64}`, AsLink: false }
      });
    }, 300);
    setTimeout(() => this.forceSave(), 1000);
  }

  private postToFrame(payload: object) {
    this.frame?.nativeElement.contentWindow?.postMessage(JSON.stringify(payload), '*');
  }
}
