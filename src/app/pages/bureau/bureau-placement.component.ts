import { Component, OnInit, OnDestroy, signal, computed, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface ZoneXY { x: number; y: number; w: number; h: number; }
interface ZoneEntry extends ZoneXY { page: number; }

@Component({
  selector: 'app-bureau-placement',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex h-screen bg-gray-100 overflow-hidden">

      <!-- Panneau gauche -->
      <div class="w-64 bg-white border-r border-gray-200 flex flex-col">
        <div class="p-4 border-b border-gray-200">
          <button (click)="back()" class="text-sm text-gray-500 hover:text-gray-800">← Retour au Bureau</button>
          <h2 class="text-sm font-semibold text-gray-800 mt-3 truncate">{{ docTitre() }}</h2>
          <p class="text-xs text-gray-400 mt-0.5">{{ pageCount() }} page(s)</p>
          @if (renvoyeMotif()) {
            <div class="mt-3 bg-orange-50 border border-orange-200 rounded-lg p-2">
              <p class="text-[10px] font-semibold text-orange-700 uppercase tracking-wide mb-0.5">Motif de renvoi</p>
              <p class="text-xs text-orange-600 italic">{{ renvoyeMotif() }}</p>
            </div>
          }
        </div>

        <!-- Thumbnails -->
        <div class="flex-1 overflow-y-auto p-3 space-y-2">
          @for (p of pages(); track p) {
            <button (click)="goToPage(p)"
                    [class]="currentPage() === p
                      ? 'w-full border-2 border-blue-500 rounded-lg overflow-hidden relative'
                      : 'w-full border-2 border-transparent rounded-lg overflow-hidden hover:border-gray-300 relative'">
              @if (thumbUrls()[p]) {
                <img [src]="thumbUrls()[p]" class="w-full block" />
              } @else {
                <div class="bg-gray-100 h-24 flex items-center justify-center text-xs text-gray-400">Page {{ p + 1 }}</div>
              }
              <!-- Badges zones sur la vignette -->
              <div class="absolute bottom-1 right-1 flex gap-0.5">
                @if (signatureZones()[p]) {
                  <span class="bg-blue-500 text-white text-[9px] px-1 rounded">✍</span>
                }
                @if (stampZones()[p]) {
                  <span class="bg-red-500 text-white text-[9px] px-1 rounded">⬤</span>
                }
              </div>
            </button>
          }
        </div>

        <!-- Actions zones pour la page courante -->
        <div class="p-3 border-t border-gray-200 space-y-2 text-xs">
          <p class="text-gray-400 font-medium uppercase tracking-wide text-[10px]">Page {{ currentPage() + 1 }}</p>

          <!-- Zone signature -->
          <button (click)="addZone('signature')"
                  class="w-full px-3 py-2 rounded-lg font-medium border-2 border-dashed border-blue-400 text-blue-600 hover:bg-blue-50">
            {{ sigZoneCurrent() ? '✓ Redessiner zone signature' : '+ Zone Signature' }}
          </button>
          @if (sigZoneCurrent()) {
            <button (click)="removeZoneOnPage('signature')"
                    class="w-full text-gray-400 hover:text-red-500">Effacer zone signature (cette page)</button>
          }
          @if (sigZoneCurrent() && pageCount() > 1) {
            <button (click)="copyToAllPages('signature')"
                    class="w-full px-2 py-1.5 rounded-lg border border-blue-200 text-blue-600 hover:bg-blue-50">
              Copier signature → toutes les pages
            </button>
          }
          @if (sigZoneCount() > 0) {
            <p class="text-gray-400">{{ sigZoneCount() }} page(s) avec zone signature</p>
          }

          <!-- Zone tampon -->
          <button (click)="addZone('stamp')"
                  class="w-full px-3 py-2 rounded-lg font-medium border-2 border-dashed border-red-400 text-red-600 hover:bg-red-50 mt-1">
            {{ stampZoneCurrent() ? '✓ Redessiner zone tampon' : '+ Zone Tampon (optionnel)' }}
          </button>
          @if (stampZoneCurrent()) {
            <button (click)="removeZoneOnPage('stamp')"
                    class="w-full text-gray-400 hover:text-red-500">Effacer zone tampon (cette page)</button>
          }
          @if (stampZoneCurrent() && pageCount() > 1) {
            <button (click)="copyToAllPages('stamp')"
                    class="w-full px-2 py-1.5 rounded-lg border border-red-200 text-red-600 hover:bg-red-50">
              Copier tampon → toutes les pages
            </button>
          }
          @if (stampZoneCount() > 0) {
            <p class="text-gray-400">{{ stampZoneCount() }} page(s) avec zone tampon</p>
          }
        </div>

        <div class="p-4 border-t border-gray-200 space-y-2">
          <button (click)="saveZones()" [disabled]="!hasAnySigZone() || saving()"
                  class="w-full bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium disabled:opacity-40">
            {{ saving() ? 'Sauvegarde…' : 'Enregistrer les zones' }}
          </button>
          @if (saved()) {
            <p class="text-xs text-green-600 text-center mt-1">✓ Zones enregistrées</p>
          }
          @if (!hasAnySigZone()) {
            <p class="text-xs text-gray-400 text-center mt-1">Au moins une zone signature requise</p>
          }

          <!-- Remplacer le PDF source — uniquement si document renvoyé par le DG -->
          @if (docStatut() === 'RETOURNE') {
            <input #replacePdfInput type="file" accept=".pdf" class="hidden"
                   (change)="onReplacePdfSelected($event)" />
            <button (click)="replacePdfInput.click()" [disabled]="replacing()"
                    class="w-full px-3 py-2 rounded-lg text-xs font-medium border border-orange-300 text-orange-600 hover:bg-orange-50 disabled:opacity-40">
              {{ replacing() ? 'Remplacement…' : '↑ Remplacer le PDF' }}
            </button>
            @if (replaced()) {
              <p class="text-xs text-green-600 text-center">✓ PDF remplacé</p>
            }
          }
        </div>
      </div>

      <!-- Viewer principal -->
      <div class="flex-1 overflow-auto flex items-start justify-center p-8">
        <div class="relative shadow-xl"
             #pageWrapper
             [style.width.px]="imgW() || null"
             [style.height.px]="imgH() || null">
          @if (pageUrl()) {
            <img [src]="pageUrl()" class="block max-w-full" (load)="onImageLoaded()" #pageImg />

            <!-- Zone signature -->
            @if (pageLoaded() && sigZoneCurrent()) {
              <div class="absolute border-2 border-dashed border-blue-500 bg-blue-50/30 cursor-move flex items-center justify-center"
                   [style.left.px]="sigZoneCurrent()!.x / 100 * imgW()"
                   [style.top.px]="sigZoneCurrent()!.y / 100 * imgH()"
                   [style.width.px]="sigZoneCurrent()!.w / 100 * imgW()"
                   [style.height.px]="sigZoneCurrent()!.h / 100 * imgH()"
                   (mousedown)="startDrag($event, 'signature')">
                <span class="text-blue-600 text-xs font-medium select-none pointer-events-none">✍️ Zone Signature</span>
              </div>
            }

            <!-- Zone tampon -->
            @if (pageLoaded() && stampZoneCurrent()) {
              <div class="absolute border-2 border-dashed border-red-500 bg-red-50/30 cursor-move flex items-center justify-center"
                   [style.left.px]="stampZoneCurrent()!.x / 100 * imgW()"
                   [style.top.px]="stampZoneCurrent()!.y / 100 * imgH()"
                   [style.width.px]="stampZoneCurrent()!.w / 100 * imgW()"
                   [style.height.px]="stampZoneCurrent()!.h / 100 * imgH()"
                   (mousedown)="startDrag($event, 'stamp')">
                <span class="text-red-600 text-xs font-medium select-none pointer-events-none">🔴 Tampon</span>
              </div>
            }

            <!-- Mode dessin -->
            @if (placingMode()) {
              <div class="absolute inset-0 cursor-crosshair"
                   (mousedown)="startDraw($event)"
                   (mousemove)="onDraw($event)"
                   (mouseup)="endDraw($event)">
                @if (drawRect()) {
                  <div class="absolute border-2 border-dashed pointer-events-none"
                       [class]="placingMode() === 'signature' ? 'border-blue-500 bg-blue-50/30' : 'border-red-500 bg-red-50/30'"
                       [style.left.px]="drawRect()!.x / 100 * imgW()"
                       [style.top.px]="drawRect()!.y / 100 * imgH()"
                       [style.width.px]="drawRect()!.w / 100 * imgW()"
                       [style.height.px]="drawRect()!.h / 100 * imgH()">
                  </div>
                }
              </div>
            }
          } @else {
            <div class="w-96 h-[560px] bg-gray-200 flex items-center justify-center text-gray-400">Chargement…</div>
          }
        </div>
      </div>
    </div>
  `
})
export class BureauPlacementComponent implements OnInit, OnDestroy {
  @ViewChild('pageWrapper')    pageWrapper!:    ElementRef<HTMLDivElement>;
  @ViewChild('pageImg')        pageImg!:        ElementRef<HTMLImageElement>;
  @ViewChild('replacePdfInput') replacePdfInput!: ElementRef<HTMLInputElement>;

  private api    = inject(ApiService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  private docId = '';
  docTitre     = signal('');
  docStatut    = signal<string>('BROUILLON');
  renvoyeMotif = signal<string | null>(null);
  pageCount    = signal(0);
  pages       = signal<number[]>([]);
  thumbUrls   = signal<Record<number, string>>({});
  currentPage = signal(0);
  pageUrl     = signal<string | null>(null);
  pageLoaded  = signal(false);
  imgW        = signal(0);
  imgH        = signal(0);

  // Per-page zone maps: key = page index
  signatureZones = signal<Record<number, ZoneXY>>({});
  stampZones     = signal<Record<number, ZoneXY>>({});

  sigZoneCurrent  = computed(() => this.signatureZones()[this.currentPage()] ?? null);
  stampZoneCurrent = computed(() => this.stampZones()[this.currentPage()] ?? null);
  sigZoneCount    = computed(() => Object.keys(this.signatureZones()).length);
  stampZoneCount  = computed(() => Object.keys(this.stampZones()).length);
  hasAnySigZone   = computed(() => this.sigZoneCount() > 0);


  placingMode = signal<'signature' | 'stamp' | null>(null);
  drawRect    = signal<ZoneXY | null>(null);
  private drawStart: { x: number; y: number } | null = null;

  saving    = signal(false);
  saved     = signal(false);
  replacing = signal(false);
  replaced  = signal(false);

  private blobUrls: string[] = [];

  ngOnInit() {
    this.docId = this.route.snapshot.paramMap.get('id') ?? '';
    this.api.getBureauDocuments().subscribe(list => {
      const doc = list.find((d: any) => d.id === this.docId);
      if (!doc) { this.router.navigate(['/bureau']); return; }
      this.docTitre.set(doc.titre);
      this.docStatut.set(doc.statut ?? 'BROUILLON');
      this.renvoyeMotif.set(doc.renvoyeMotif ?? null);
      this.pageCount.set(doc.pageCount ?? 1);
      this.pages.set(Array.from({ length: doc.pageCount ?? 1 }, (_, i) => i));

      if (doc.signatureZones?.length) {
        const m: Record<number, ZoneXY> = {};
        (doc.signatureZones as ZoneEntry[]).forEach(z => { m[z.page] = { x: z.x, y: z.y, w: z.w, h: z.h }; });
        this.signatureZones.set(m);
      }
      if (doc.stampZones?.length) {
        const m: Record<number, ZoneXY> = {};
        (doc.stampZones as ZoneEntry[]).forEach(z => { m[z.page] = { x: z.x, y: z.y, w: z.w, h: z.h }; });
        this.stampZones.set(m);
      }

      this.loadPage(0);
      this.loadThumbs();
    });
  }

  ngOnDestroy() { this.blobUrls.forEach(u => URL.revokeObjectURL(u)); }

  back() { this.router.navigate(['/bureau']); }

  private loadPage(p: number) {
    this.currentPage.set(p);
    this.pageUrl.set(null);
    this.pageLoaded.set(false);
    this.imgW.set(0);
    this.imgH.set(0);
    this.api.getBureauPage(this.docId, p).subscribe(url => {
      this.blobUrls.push(url);
      this.pageUrl.set(url);
    });
  }

  private loadThumbs() {
    const n = this.pageCount();
    for (let p = 0; p < n; p++) {
      this.api.getBureauPage(this.docId, p).subscribe(url => {
        this.blobUrls.push(url);
        this.thumbUrls.update(m => ({ ...m, [p]: url }));
      });
    }
  }

  goToPage(p: number) { this.loadPage(p); }

  onImageLoaded() {
    const el = this.pageImg.nativeElement;
    this.imgW.set(el.offsetWidth);
    this.imgH.set(el.offsetHeight);
    this.pageLoaded.set(true);
  }

  addZone(type: 'signature' | 'stamp') {
    this.placingMode.set(type);
    this.drawRect.set(null);
  }

  removeZoneOnPage(type: 'signature' | 'stamp') {
    const p = this.currentPage();
    if (type === 'signature') {
      this.signatureZones.update(m => { const n = { ...m }; delete n[p]; return n; });
    } else {
      this.stampZones.update(m => { const n = { ...m }; delete n[p]; return n; });
    }
    this.saved.set(false);
  }

  copyToAllPages(type: 'signature' | 'stamp') {
    const zone = type === 'signature' ? this.sigZoneCurrent() : this.stampZoneCurrent();
    if (!zone) return;
    const newMap: Record<number, ZoneXY> = {};
    for (let p = 0; p < this.pageCount(); p++) newMap[p] = { ...zone };
    if (type === 'signature') this.signatureZones.set(newMap);
    else                      this.stampZones.set(newMap);
    this.saved.set(false);
  }

  startDraw(ev: MouseEvent) {
    if (!this.placingMode()) return;
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    this.drawStart = {
      x: ((ev.clientX - rect.left) / rect.width)  * 100,
      y: ((ev.clientY - rect.top)  / rect.height) * 100,
    };
    ev.preventDefault();
  }

  onDraw(ev: MouseEvent) {
    if (!this.drawStart || !this.placingMode()) return;
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const cx = ((ev.clientX - rect.left) / rect.width)  * 100;
    const cy = ((ev.clientY - rect.top)  / rect.height) * 100;
    this.drawRect.set({
      x: Math.min(this.drawStart.x, cx),
      y: Math.min(this.drawStart.y, cy),
      w: Math.abs(cx - this.drawStart.x),
      h: Math.abs(cy - this.drawStart.y),
    });
    ev.preventDefault();
  }

  endDraw(ev: MouseEvent) {
    if (!this.drawStart || !this.placingMode()) return;
    const dr = this.drawRect();
    if (dr && dr.w > 2 && dr.h > 1) {
      const w = Math.min(dr.w, 100);
      const h = Math.min(dr.h, 100);
      const x = Math.max(0, Math.min(dr.x, 100 - w));
      const y = Math.max(0, Math.min(dr.y, 100 - h));
      const p = this.currentPage();
      if (this.placingMode() === 'signature') {
        this.signatureZones.update(m => ({ ...m, [p]: { x, y, w, h } }));
      } else {
        this.stampZones.update(m => ({ ...m, [p]: { x, y, w, h } }));
      }
    }
    this.placingMode.set(null);
    this.drawRect.set(null);
    this.drawStart = null;
    this.saved.set(false);
    ev.preventDefault();
  }

  // ── Drag zone existante ──────────────────────────────────────────────────

  private dragType: 'signature' | 'stamp' | null = null;
  private dragOffset: { dx: number; dy: number } = { dx: 0, dy: 0 };
  private boundMove = this.onGlobalMove.bind(this);
  private boundUp   = this.onGlobalUp.bind(this);

  startDrag(ev: MouseEvent, type: 'signature' | 'stamp') {
    if (this.placingMode()) return;
    const zone = type === 'signature' ? this.sigZoneCurrent() : this.stampZoneCurrent();
    if (!zone) return;
    this.dragType = type;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    const cx = ((ev.clientX - rect.left) / rect.width)  * 100;
    const cy = ((ev.clientY - rect.top)  / rect.height) * 100;
    this.dragOffset = { dx: cx - zone.x, dy: cy - zone.y };
    document.addEventListener('mousemove', this.boundMove);
    document.addEventListener('mouseup',   this.boundUp);
    ev.preventDefault();
  }

  private onGlobalMove(ev: MouseEvent) {
    if (!this.dragType) return;
    const zone = this.dragType === 'signature' ? this.sigZoneCurrent() : this.stampZoneCurrent();
    if (!zone) return;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    const cx = ((ev.clientX - rect.left) / rect.width)  * 100;
    const cy = ((ev.clientY - rect.top)  / rect.height) * 100;
    const p = this.currentPage();
    const newX = Math.max(0, Math.min(cx - this.dragOffset.dx, 100 - zone.w));
    const newY = Math.max(0, Math.min(cy - this.dragOffset.dy, 100 - zone.h));
    if (this.dragType === 'signature') {
      this.signatureZones.update(m => ({ ...m, [p]: { ...zone, x: newX, y: newY } }));
    } else {
      this.stampZones.update(m => ({ ...m, [p]: { ...zone, x: newX, y: newY } }));
    }
  }

  private onGlobalUp() {
    this.dragType = null;
    this.saved.set(false);
    document.removeEventListener('mousemove', this.boundMove);
    document.removeEventListener('mouseup',   this.boundUp);
  }

  // ── Remplacement du PDF source ───────────────────────────────────────────

  onReplacePdfSelected(ev: Event) {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.replacing.set(true);
    this.replaced.set(false);
    this.api.replaceBureauPdf(this.docId, file).subscribe({
      next: (doc: any) => {
        this.replacing.set(false);
        this.replaced.set(true);
        // Réinitialiser l'input pour permettre re-sélection du même fichier
        this.replacePdfInput.nativeElement.value = '';
        // Mettre à jour le nombre de pages et recharger les vignettes + page courante
        const newCount = doc.pageCount ?? 1;
        this.pageCount.set(newCount);
        this.pages.set(Array.from({ length: newCount }, (_, i) => i));
        this.blobUrls.forEach(u => URL.revokeObjectURL(u));
        this.blobUrls = [];
        this.thumbUrls.set({});
        this.loadPage(0);
        this.loadThumbs();
      },
      error: () => this.replacing.set(false),
    });
  }

  // ── Sauvegarde ───────────────────────────────────────────────────────────

  saveZones() {
    if (!this.hasAnySigZone()) return;
    this.saving.set(true);
    const sigArray: ZoneEntry[] = Object.entries(this.signatureZones())
      .map(([page, z]) => ({ page: +page, ...z }));
    const stArray: ZoneEntry[] = Object.entries(this.stampZones())
      .map(([page, z]) => ({ page: +page, ...z }));
    this.api.saveBureauZones(this.docId, { signatureZones: sigArray, stampZones: stArray }).subscribe({
      next: () => { this.saving.set(false); this.saved.set(true); },
      error: () => this.saving.set(false),
    });
  }
}
