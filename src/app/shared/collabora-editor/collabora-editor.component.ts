import {
  Component, ElementRef, OnDestroy, OnInit, ViewChild,
  inject, input, output, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { FormsModule } from '@angular/forms';

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
  imports: [CommonModule, FormsModule],
  template: `
    <div class="relative w-full h-full flex overflow-hidden">

      <!-- ── Iframe Collabora ─────────────────────────────────────────── -->
      <div class="relative flex-1 flex flex-col min-w-0">

        <!-- Loader -->
        @if (loading()) {
          <div class="absolute inset-0 z-10 flex items-center justify-center bg-slate-50">
            <div class="flex flex-col items-center gap-3">
              <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
              <span class="text-slate-500 text-sm">Chargement de l'éditeur sécurisé…</span>
            </div>
          </div>
        }

        <!-- iframe Collabora -->
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

      <!-- ── Panneau IA latéral (visible si canWrite + contexte BUREAU) ── -->
      @if (canWrite() && context() === 'BUREAU') {
        <div class="w-64 flex-shrink-0 border-l border-gray-200 bg-gray-50 flex flex-col overflow-y-auto">

          <!-- En-tête -->
          <div class="px-4 py-3 border-b border-gray-200 flex items-center gap-2">
            <span class="text-base">🤖</span>
            <h3 class="text-sm font-semibold text-gray-700">Assistant IA</h3>
          </div>

          <div class="p-3 flex flex-col gap-2">

            <!-- Boutons d'action -->
            <button (click)="aiAssist('IMPROVE_STYLE')"
                    [disabled]="aiPending()"
                    class="w-full px-3 py-2 rounded-lg text-xs font-medium text-left transition-colors
                           bg-white border border-indigo-200 text-indigo-700 hover:bg-indigo-50
                           disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2">
              <span>✨</span><span>Améliorer le style</span>
            </button>

            <button (click)="aiAssist('CORRECT_GRAMMAR')"
                    [disabled]="aiPending()"
                    class="w-full px-3 py-2 rounded-lg text-xs font-medium text-left transition-colors
                           bg-white border border-indigo-200 text-indigo-700 hover:bg-indigo-50
                           disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2">
              <span>🔤</span><span>Corriger la grammaire</span>
            </button>

            <button (click)="aiAssist('SUMMARIZE')"
                    [disabled]="aiPending()"
                    class="w-full px-3 py-2 rounded-lg text-xs font-medium text-left transition-colors
                           bg-white border border-indigo-200 text-indigo-700 hover:bg-indigo-50
                           disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2">
              <span>📋</span><span>Résumer en objet</span>
            </button>

            <!-- Spinner en cours -->
            @if (aiPending()) {
              <div class="flex items-center gap-2 mt-1 text-xs text-indigo-600">
                <div class="w-3 h-3 border-2 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
                <span>Traitement en cours…</span>
              </div>
            }

            <!-- Résultat / statut -->
            @if (aiStatus()) {
              <div class="mt-1 p-2 rounded-lg text-xs"
                   [class]="aiStatusIsError()
                     ? 'bg-red-50 text-red-700 border border-red-200'
                     : 'bg-green-50 text-green-700 border border-green-200'">
                {{ aiStatus() }}
              </div>
            }

            <!-- Suggestion retournée (mode suggestion sans fil d'instruction) -->
            @if (aiSuggestion()) {
              <div class="mt-2 flex flex-col gap-1">
                <p class="text-[10px] font-semibold text-gray-500 uppercase tracking-wide">Suggestion IA</p>
                <div class="p-2 bg-white border border-gray-200 rounded-lg text-xs text-gray-700 max-h-64 overflow-y-auto whitespace-pre-wrap leading-relaxed">
                  {{ aiSuggestion() }}
                </div>
              </div>
            }

            <!-- Séparateur + aide contextuelle -->
            <div class="mt-3 pt-3 border-t border-gray-200">
              <p class="text-[10px] text-gray-400 leading-snug">
                La suggestion est postée dans le fil d'instruction associé au document.
                Applique-la manuellement dans l'éditeur si elle te convient.
              </p>
            </div>
          </div>
        </div>
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

  // ── État panneau IA ───────────────────────────────────────────────────────
  aiPending       = signal(false);
  aiStatus        = signal('');
  aiStatusIsError = signal(false);
  aiSuggestion    = signal('');

  @ViewChild('collaboraFrame') frame!: ElementRef<HTMLIFrameElement>;

  private api       = inject(ApiService);
  private sanitizer = inject(DomSanitizer);
  private toast     = inject(ToastService);

  private messageHandler = (ev: MessageEvent) => this.onCollaboraMessage(ev);
  private sseSource: EventSource | null = null;

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    window.addEventListener('message', this.messageHandler);
    this.initSession();
    this.listenForReload();
  }

  ngOnDestroy() {
    window.removeEventListener('message', this.messageHandler);
    this.sseSource?.close();
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

  // ── Assistant IA ──────────────────────────────────────────────────────────

  aiAssist(action: 'IMPROVE_STYLE' | 'CORRECT_GRAMMAR' | 'SUMMARIZE') {
    if (this.aiPending()) return;
    this.aiPending.set(true);
    this.aiStatus.set('');
    this.aiStatusIsError.set(false);
    this.aiSuggestion.set('');

    // Forcer la sauvegarde d'abord pour que le backend ait la dernière version
    this.forceSave();

    // Laisser le temps au PutFile WOPI de se propager (~1.5s)
    setTimeout(() => {
      this.api.aiSuggest(this.bureauDocumentId(), action).subscribe({
        next: (res: any) => {
          if (res.mode === 'AUTO_APPLY') {
            // Le backend a modifié le fichier — rechargement via SSE RELOAD_IFRAME
            this.aiStatus.set(`✅ ${this.actionLabel(action)} appliqué. Rechargement…`);
          } else {
            // Mode SUGGESTION
            if (res.postedToInstruction) {
              this.aiStatus.set(`✅ Suggestion postée dans le fil d'instruction.`);
            } else {
              this.aiStatus.set(`✅ Suggestion générée.`);
              this.aiSuggestion.set(res.suggestion ?? '');
            }
            this.aiPending.set(false);
          }
        },
        error: (err: any) => {
          this.aiPending.set(false);
          this.aiStatusIsError.set(true);
          const msg = err?.error?.error ?? 'Erreur de l\'assistant IA';
          this.aiStatus.set(msg);
        }
      });
    }, 1500);
  }

  private actionLabel(action: string): string {
    switch (action) {
      case 'IMPROVE_STYLE':   return 'Amélioration du style';
      case 'CORRECT_GRAMMAR': return 'Correction grammaticale';
      case 'SUMMARIZE':       return 'Résumé';
      default: return action;
    }
  }

  // Écoute SSE RELOAD_IFRAME : déclenché par le mode AUTO-APPLY
  private listenForReload() {
    this.sseSource = new EventSource('/api/events');
    this.sseSource.addEventListener('RELOAD_IFRAME', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data);
        if (data.bureauDocumentId === this.bureauDocumentId()) {
          this.aiPending.set(false);
          // Recharger la session WOPI (nouveau token + fichier mis à jour)
          this.iframeSrc.set(null);
          this.initSession();
        }
      } catch { /* ignore */ }
    });
  }

  private postToFrame(payload: object) {
    this.frame?.nativeElement.contentWindow?.postMessage(JSON.stringify(payload), '*');
  }
}
