import { Component, signal, computed, inject, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AudioRecorderComponent } from '../../shared/audio-recorder/audio-recorder.component';

interface Highlight { x: number; y: number; w: number; h: number; }

@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  imports: [CommonModule, FormsModule, AudioRecorderComponent],
  template: `
    <div class="flex h-screen bg-gray-100 overflow-hidden">

      <!-- Panneau gauche -->
      <div class="w-56 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
        <div class="p-4 border-b border-gray-200">
          <button (click)="back()" class="text-sm text-blue-600 hover:underline">&larr; Retour</button>
          <h2 class="font-semibold text-gray-800 mt-2 truncate text-sm">{{ doc()?.title || 'Document' }}</h2>
          @if (isParapheur()) {
            <span class="mt-1 inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-100 text-amber-800">
              En attente de signature
            </span>
          }
          @if (doc()?.status === 'FINALIZED') {
            <span class="mt-1 inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold bg-green-100 text-green-800">
              Signé
            </span>
          }
        </div>

        <!-- Thumbnails -->
        <div class="flex-1 overflow-y-auto p-2 space-y-2">
          @for (p of pages(); track p) {
            <div (click)="goToPage(p)"
                 [class]="currentPage() === p
                   ? 'border-2 border-blue-500 rounded-lg overflow-hidden cursor-pointer relative'
                   : 'border border-gray-200 rounded-lg overflow-hidden cursor-pointer hover:border-blue-300 relative'">
              @if (thumbUrls()[p]) {
                <img [src]="thumbUrls()[p]" class="w-full object-contain block" />
              } @else {
                <div class="bg-gray-100 h-16 flex items-center justify-center text-xs text-gray-400">{{ p + 1 }}</div>
              }
              <p class="text-center text-xs text-gray-500 py-1">{{ p + 1 }}</p>
              <!-- Badge zones signature sur vignette -->
              @if (signatureAnnotations()[p]?.length) {
                <span class="absolute top-1 left-1 bg-blue-500 text-white text-[9px] font-bold px-1 rounded">
                  {{ signatureAnnotations()[p].length }}✍️
                </span>
              }
              <!-- Badge zones surlignées sur vignette -->
              @if (highlights()[p] && highlights()[p].length) {
                <span class="absolute top-1 right-1 bg-yellow-400 text-yellow-900 text-[9px] font-bold px-1 rounded">
                  {{ highlights()[p].length }}🖊️
                </span>
              }
            </div>
          }
        </div>
      </div>

      <!-- Zone principale -->
      <div class="flex-1 flex flex-col overflow-hidden">

        <!-- Toolbar -->
        <div class="bg-white border-b border-gray-200 px-4 py-2 flex items-center gap-3 flex-wrap">
          <span class="text-sm text-gray-500">Page {{ currentPage() + 1 }} / {{ pages().length }}</span>
          <div class="flex-1"></div>

          @if (isParapheur()) {
            <!-- Bouton surligner -->
            <button (click)="toggleHighlightMode()"
                    [class]="highlightMode()
                      ? 'px-3 py-1.5 bg-yellow-400 text-yellow-900 text-sm rounded-lg font-medium flex items-center gap-1.5'
                      : 'px-3 py-1.5 border border-yellow-400 text-yellow-700 text-sm rounded-lg hover:bg-yellow-50 flex items-center gap-1.5'">
              🖊️
              <span>{{ highlightMode() ? 'Terminer' : 'Surligner' }}</span>
              @if (totalHighlights() > 0) {
                <span class="bg-yellow-600 text-white text-[10px] px-1.5 py-0.5 rounded-full">{{ totalHighlights() }}</span>
              }
            </button>

            <button (click)="openCorrectionModal()"
                    class="px-4 py-1.5 border border-amber-300 text-amber-700 text-sm rounded-lg hover:bg-amber-50">
              ✏️ Correction/modification demandées
            </button>
            <button (click)="openRejectModal()"
                    class="px-4 py-1.5 border border-gray-300 text-gray-700 text-sm rounded-lg hover:bg-red-50 hover:text-red-600 hover:border-red-300">
              Refuser
            </button>
            <div class="relative group/sign">
              <button (click)="signerDocument()" [disabled]="!canSign()"
                      class="px-4 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2 transition-opacity">
                @if (signing()) { <span class="w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin inline-block"></span> }
                {{ signing() ? 'Signature en cours…' : '✍️ Signer le document' }}
              </button>
              @if (!canSign() && !signing()) {
                <div class="absolute bottom-full right-0 mb-1.5 w-56 bg-gray-800 text-white text-xs rounded-lg px-3 py-2 shadow-lg pointer-events-none opacity-0 group-hover/sign:opacity-100 transition-opacity z-20 text-center leading-snug">
                  @if (highlightMode()) {
                    Terminez le mode surlignage avant de signer
                  } @else {
                    Envoyez les corrections surlignées avant de signer
                  }
                </div>
              }
            </div>
          }

          @if (doc()?.status === 'FINALIZED') {
            <button (click)="api.downloadFinalPdfBlob(docId, (doc()?.title || 'document') + '.pdf')"
                    class="px-4 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
              Télécharger PDF final
            </button>
          }
        </div>

        <!-- Instruction mode surlignage -->
        @if (highlightMode()) {
          <div class="bg-yellow-50 border-b border-yellow-200 px-4 py-1.5 text-xs text-yellow-800 flex items-center gap-2">
            <span>🖊️</span>
            <span>Cliquez-glissez pour surligner une zone. Survolez une zone pour la supprimer.</span>
          </div>
        }

        <!-- Page courante -->
        <div class="flex-1 overflow-auto flex items-start justify-center p-6 bg-gray-100">
          @if (pageUrl()) {
            <div class="relative inline-block shadow-xl"
                 #pageWrapper
                 [style.width.px]="imgW() || null"
                 [style.height.px]="imgH() || null"
                 [class.cursor-crosshair]="highlightMode()"
                 (mousedown)="startDrawHighlight($event)">

              <img [src]="pageUrl()!" class="block max-w-full select-none"
                   #pageImg
                   (load)="onPageImageLoaded()"
                   [class.pointer-events-none]="highlightMode()" />

              <!-- Zones surlignées existantes -->
              @for (h of currentPageHighlights(); track $index) {
                <div class="absolute bg-yellow-300/50 border-2 border-yellow-500 group"
                     [style.left.%]="h.x"
                     [style.top.%]="h.y"
                     [style.width.%]="h.w"
                     [style.height.%]="h.h"
                     (mousedown)="$event.stopPropagation()">
                  <button (click)="removeHighlight($index)"
                          class="absolute -top-3 -right-3 w-5 h-5 rounded-full bg-red-500 text-white text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow">
                    ×
                  </button>
                </div>
              }

              <!-- Aperçu du dessin en cours -->
              @if (drawingHighlight()) {
                <div class="absolute border-2 border-dashed border-yellow-600 bg-yellow-300/30 pointer-events-none"
                     [style.left.%]="drawingHighlight()!.x"
                     [style.top.%]="drawingHighlight()!.y"
                     [style.width.%]="drawingHighlight()!.w"
                     [style.height.%]="drawingHighlight()!.h">
                </div>
              }
            </div>
          } @else {
            <div class="flex items-center justify-center h-full text-gray-400">Chargement…</div>
          }
        </div>
      </div>
    </div>

    <!-- Modal Refus -->
    @if (showRejectModal()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
           (click)="$event.target === $event.currentTarget && showRejectModal.set(false)">
        <div class="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
          <h2 class="text-base font-semibold text-gray-900 mb-1">Refuser le document</h2>
          <p class="text-xs text-gray-500 mb-4">Indiquez le motif du refus.</p>
          <textarea rows="3" [(ngModel)]="rejectCommentValue"
                    placeholder="Motif du refus…"
                    class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-red-300">
          </textarea>
          <div class="flex gap-3 mt-4">
            <button (click)="showRejectModal.set(false)"
                    class="flex-1 px-4 py-2 border border-gray-200 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
              Annuler
            </button>
            <button (click)="confirmReject()" [disabled]="rejecting() || !rejectCommentValue.trim()"
                    class="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-50">
              {{ rejecting() ? 'En cours…' : 'Confirmer le refus' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal Correction/modification demandées -->
    @if (showCorrectionModal()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
           (click)="$event.target === $event.currentTarget && showCorrectionModal.set(false)">
        <div class="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
          <h2 class="text-base font-semibold text-gray-900 mb-1">Correction/modification demandées</h2>
          <p class="text-xs text-gray-500 mb-4">
            Le document sera renvoyé à la secrétaire et une instruction sera créée automatiquement.
          </p>

          <!-- Résumé des zones surlignées -->
          @if (totalHighlights() > 0) {
            <div class="bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2 mb-4 flex items-center gap-2">
              <span class="text-yellow-600 text-base">🖊️</span>
              <p class="text-xs text-yellow-700">
                <strong>{{ totalHighlights() }}</strong> zone(s) surlignée(s) sur <strong>{{ highlightedPageCount() }}</strong> page(s) jointes à la correction
              </p>
            </div>
          }

          <!-- Commentaire texte -->
          <label class="block text-xs font-medium text-gray-600 mb-1">Description des corrections <span class="text-red-500">*</span></label>
          <textarea rows="3" [(ngModel)]="correctionComment"
                    placeholder="Décrivez les corrections à apporter…"
                    class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-amber-300 mb-4">
          </textarea>

          <!-- Message vocal optionnel -->
          <label class="block text-xs font-medium text-gray-600 mb-2">Message vocal (optionnel)</label>
          @if (!correctionAudio()) {
            <div class="flex items-center gap-2 px-3 py-2 border border-dashed border-gray-300 rounded-lg mb-4 bg-gray-50">
              <app-audio-recorder (recorded)="onCorrectionAudioRecorded($event)"></app-audio-recorder>
              <span class="text-xs text-gray-400">Enregistrez un mémo vocal pour accompagner vos corrections</span>
            </div>
          } @else {
            <div class="flex items-center gap-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg mb-4">
              <span class="text-amber-600">🎙️</span>
              <span class="text-xs text-amber-700 flex-1 truncate">{{ correctionAudio()!.name }}</span>
              <button (click)="correctionAudio.set(null)" class="text-xs text-gray-400 hover:text-red-500 flex-shrink-0">✕ Supprimer</button>
            </div>
          }

          <div class="flex gap-3">
            <button (click)="showCorrectionModal.set(false)"
                    class="flex-1 px-4 py-2 border border-gray-200 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
              Annuler
            </button>
            <button (click)="confirmCorrection()" [disabled]="sendingCorrection() || !correctionComment.trim()"
                    class="flex-1 px-4 py-2 bg-amber-500 text-white rounded-lg text-sm font-medium hover:bg-amber-600 disabled:opacity-50">
              {{ sendingCorrection() ? 'Envoi…' : 'Envoyer les corrections' }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class PdfViewerComponent implements OnInit, OnDestroy {
  @ViewChild('pageWrapper') pageWrapper!: ElementRef<HTMLDivElement>;
  @ViewChild('pageImg')    pageImg!:    ElementRef<HTMLImageElement>;

  api    = inject(ApiService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  docId = '';
  doc         = signal<any>(null);
  pages       = signal<number[]>([]);
  thumbUrls   = signal<Record<number, string>>({});
  currentPage = signal(0);
  pageUrl     = signal<string | null>(null);

  signing         = signal(false);
  showRejectModal = signal(false);
  rejectCommentValue = '';
  rejecting       = signal(false);

  private cameFromParapheur = false;

  showCorrectionModal = signal(false);
  correctionComment   = '';
  correctionAudio     = signal<File | null>(null);
  sendingCorrection   = signal(false);

  isParapheur = signal(false);

  // ── Dimensions image courante (capturées au chargement) ──────────────────
  imgW = signal(0);
  imgH = signal(0);

  // ── Zones signature/tampon (chargées depuis les annotations) ─────────────
  signatureAnnotations     = signal<Record<number, any[]>>({});
  currentPageAnnotations   = computed(() => this.signatureAnnotations()[this.currentPage()] ?? []);

  // ── Surlignage ───────────────────────────────────────────────────────────
  highlights      = signal<Record<number, Highlight[]>>({});
  highlightMode   = signal(false);
  drawingHighlight = signal<Highlight | null>(null);

  currentPageHighlights = computed(() => this.highlights()[this.currentPage()] ?? []);
  totalHighlights       = computed(() => Object.values(this.highlights()).reduce((s, a) => s + a.length, 0));
  highlightedPageCount  = computed(() => Object.values(this.highlights()).filter(a => a.length > 0).length);

  canSign = computed(() => !this.highlightMode() && this.totalHighlights() === 0 && !this.signing());

  private hlStart: { x: number; y: number } | null = null;
  private hlBoundMove = this.onHlMove.bind(this);
  private hlBoundUp   = this.onHlUp.bind(this);

  private blobUrls: string[] = [];

  ngOnInit() {
    this.docId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadDoc();
  }

  ngOnDestroy() {
    this.blobUrls.forEach(u => URL.revokeObjectURL(u));
    document.removeEventListener('mousemove', this.hlBoundMove);
    document.removeEventListener('mouseup', this.hlBoundUp);
  }

  back() {
    if (this.isParapheur() || this.cameFromParapheur) this.router.navigate(['/signature']);
    else                                               this.router.navigate(['/pdf-documents']);
  }

  loadDoc() {
    this.api.getPdfDocuments().subscribe((list: any[]) => {
      const d = list.find(x => x.id === this.docId);
      if (!d) return;
      this.doc.set(d);
      this.isParapheur.set(d.parapheurStatut === 'EN_ATTENTE_SIGNATURE');
      if (d.parapheurStatut != null) this.cameFromParapheur = true;
      const count = d.pageCount ?? 1;
      this.pages.set(Array.from({ length: count }, (_, i) => i));
      this.goToPage(0);
      this.loadThumbs(count);
      if (d.parapheurStatut === 'EN_ATTENTE_SIGNATURE') {
        this.loadSignatureAnnotations(count);
      }
    });
  }

  private loadSignatureAnnotations(pageCount: number) {
    for (let p = 0; p < pageCount; p++) {
      const pageId = `${this.docId}::${p}`;
      this.api.getAnnotations(pageId).subscribe(annots => {
        const zones = annots.filter((a: any) =>
          a.annotationType === 'SIGNATURE_ZONE' || a.annotationType === 'STAMP_ZONE'
        );
        if (zones.length > 0) {
          this.signatureAnnotations.update(m => ({ ...m, [p]: zones }));
        }
      });
    }
  }

  private loadThumbs(count: number) {
    for (let p = 0; p < count; p++) {
      this.api.renderPageBlob(this.docId, p).subscribe(url => {
        this.blobUrls.push(url);
        this.thumbUrls.update(m => ({ ...m, [p]: url }));
      });
    }
  }

  onPageImageLoaded() {
    const el = this.pageImg?.nativeElement;
    if (el) {
      this.imgW.set(el.offsetWidth);
      this.imgH.set(el.offsetHeight);
    }
  }

  goToPage(p: number) {
    this.currentPage.set(p);
    this.pageUrl.set(null);
    this.imgW.set(0);
    this.imgH.set(0);
    const fetch$ = this.isParapheur()
      ? this.api.renderPageWithZonesBlob(this.docId, p)
      : this.api.renderPageBlob(this.docId, p);
    fetch$.subscribe(url => {
      this.blobUrls.push(url);
      this.pageUrl.set(url);
    });
  }

  signerDocument() {
    this.signing.set(true);
    this.api.signerDocument(this.docId).subscribe({
      next: (signed: any) => {
        this.signing.set(false);
        this.doc.set(signed);
        this.isParapheur.set(false);
        const count = signed.pageCount ?? 1;
        this.blobUrls.forEach(u => URL.revokeObjectURL(u));
        this.blobUrls = [];
        this.thumbUrls.set({});
        this.pageUrl.set(null);
        this.pages.set(Array.from({ length: count }, (_, i) => i));
        this.goToPage(0);
        this.loadThumbs(count);
      },
      error: () => this.signing.set(false),
    });
  }

  openRejectModal() {
    this.rejectCommentValue = '';
    this.showRejectModal.set(true);
  }

  confirmReject() {
    if (!this.rejectCommentValue.trim()) return;
    this.rejecting.set(true);
    this.api.rejeterDocument(this.docId, this.rejectCommentValue).subscribe({
      next: () => { this.rejecting.set(false); this.router.navigate(['/signature']); },
      error: () => this.rejecting.set(false),
    });
  }

  // ── Surlignage ───────────────────────────────────────────────────────────

  toggleHighlightMode() {
    this.highlightMode.update(v => !v);
    if (!this.highlightMode()) {
      this.drawingHighlight.set(null);
      this.hlStart = null;
    }
  }

  startDrawHighlight(ev: MouseEvent) {
    if (!this.highlightMode() || !this.pageWrapper) return;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    this.hlStart = {
      x: Math.max(0, Math.min(((ev.clientX - rect.left) / rect.width) * 100, 100)),
      y: Math.max(0, Math.min(((ev.clientY - rect.top) / rect.height) * 100, 100)),
    };
    document.addEventListener('mousemove', this.hlBoundMove);
    document.addEventListener('mouseup', this.hlBoundUp);
    ev.preventDefault();
  }

  private onHlMove(ev: MouseEvent) {
    if (!this.hlStart || !this.pageWrapper) return;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    const cx = Math.max(0, Math.min(((ev.clientX - rect.left) / rect.width) * 100, 100));
    const cy = Math.max(0, Math.min(((ev.clientY - rect.top) / rect.height) * 100, 100));
    this.drawingHighlight.set({
      x: Math.min(this.hlStart.x, cx),
      y: Math.min(this.hlStart.y, cy),
      w: Math.abs(cx - this.hlStart.x),
      h: Math.abs(cy - this.hlStart.y),
    });
  }

  private onHlUp() {
    const dr = this.drawingHighlight();
    if (dr && dr.w > 1 && dr.h > 0.5) {
      const p = this.currentPage();
      this.highlights.update(m => ({
        ...m,
        [p]: [...(m[p] ?? []), { x: dr.x, y: dr.y, w: dr.w, h: dr.h }],
      }));
    }
    this.drawingHighlight.set(null);
    this.hlStart = null;
    document.removeEventListener('mousemove', this.hlBoundMove);
    document.removeEventListener('mouseup', this.hlBoundUp);
  }

  removeHighlight(index: number) {
    const p = this.currentPage();
    this.highlights.update(m => ({
      ...m,
      [p]: (m[p] ?? []).filter((_, i) => i !== index),
    }));
  }

  // ── Modal correction ─────────────────────────────────────────────────────

  openCorrectionModal() {
    this.correctionComment = '';
    this.correctionAudio.set(null);
    this.showCorrectionModal.set(true);
  }

  onCorrectionAudioRecorded(blob: Blob) {
    const file = new File([blob], `correction_${Date.now()}.webm`, { type: blob.type || 'audio/webm' });
    this.correctionAudio.set(file);
  }

  confirmCorrection() {
    if (!this.correctionComment.trim()) return;
    this.sendingCorrection.set(true);

    const hlJson = this.totalHighlights() > 0
      ? JSON.stringify(
          Object.entries(this.highlights())
            .flatMap(([page, zones]) => zones.map(z => ({ page: +page, ...z })))
        )
      : undefined;

    this.api.envoyerCorrection(
      this.docId, this.correctionComment, this.correctionAudio() ?? undefined, hlJson
    ).subscribe({
      next: () => { this.sendingCorrection.set(false); this.router.navigate(['/signature']); },
      error: () => this.sendingCorrection.set(false),
    });
  }
}
