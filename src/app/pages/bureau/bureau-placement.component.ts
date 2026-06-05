import { Component, OnInit, OnDestroy, signal, computed, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface ZoneXY { x: number; y: number; w: number; h: number; }
interface ZoneEntry extends ZoneXY { page: number; userId?: string; userNom?: string; }
interface Signataire { userId: string; userNom: string; }

const SIGNER_COLORS = ['#3B82F6', '#8B5CF6', '#10B981', '#F97316'];

@Component({
  selector: 'app-bureau-placement',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
                    [class]="'w-full border-2 rounded-lg overflow-hidden relative ' +
                      (currentPage() === p ? 'border-blue-500' : 'border-transparent hover:border-gray-300')">
              @if (thumbUrls()[p]) {
                <img [src]="thumbUrls()[p]" class="w-full block" />
              } @else {
                <div class="bg-gray-100 h-24 flex items-center justify-center text-xs text-gray-400">Page {{ p + 1 }}</div>
              }
              <div class="absolute bottom-1 right-1 flex gap-0.5">
                @if (sigZonesOnPage(p).length > 0) {
                  <span class="bg-blue-500 text-white text-[9px] px-1 rounded">✍ {{ sigZonesOnPage(p).length }}</span>
                }
                @if (stampZones()[p]) {
                  <span class="bg-red-500 text-white text-[9px] px-1 rounded">⬤</span>
                }
              </div>
            </button>
          }
        </div>

        <!-- Actions -->
        <div class="p-3 border-t border-gray-200 space-y-2 text-xs">
          <p class="text-gray-400 font-medium uppercase tracking-wide text-[10px]">Page {{ currentPage() + 1 }}</p>

          <!-- Sélecteur de signataire (workflow) ou mode anonyme (legacy) -->
          @if (signataires().length > 1) {
            <div>
              <label class="block text-[10px] text-gray-400 uppercase mb-1">Zone pour :</label>
              <select [(ngModel)]="activeSignerUserId"
                      class="w-full border border-gray-200 rounded px-2 py-1 text-xs">
                <option value="">-- Choisir un signataire --</option>
                @for (s of signataires(); track s.userId) {
                  <option [value]="s.userId">{{ s.userNom }}</option>
                }
              </select>
            </div>
          } @else if (signataires().length === 1) {
            <p class="text-[10px] text-gray-500">
              Zone pour : <span class="font-medium text-gray-700">{{ signataires()[0].userNom }}</span>
            </p>
          }

          <!-- Zone signature -->
          <button (click)="addZone('signature')"
                  class="w-full px-3 py-2 rounded-lg font-medium border-2 border-dashed border-blue-400 text-blue-600 hover:bg-blue-50">
            {{ sigZonesOnPage(currentPage()).length > 0 ? '+ Ajouter / Redessiner zone signature' : '+ Zone Signature' }}
          </button>
          @for (z of sigZonesOnPage(currentPage()); track $index) {
            <div class="flex items-center gap-1 text-[10px] text-gray-500">
              <span class="w-2 h-2 rounded-full shrink-0" [style.background]="zoneColor(z.userId)"></span>
              <span class="flex-1 truncate">{{ z.userNom ?? 'Zone signature' }}</span>
              <button (click)="removeZone(z)" class="text-red-400 hover:text-red-600 ml-1">✕</button>
            </div>
          }
          @if (sigZonesOnPage(currentPage()).length > 0 && pageCount() > 1) {
            <button (click)="copyToAllPages()"
                    class="w-full px-2 py-1.5 rounded-lg border border-blue-200 text-blue-600 hover:bg-blue-50">
              Copier signature → toutes les pages
            </button>
          }
          @if (totalSigZones() > 0) {
            <p class="text-gray-400">{{ totalSigZones() }} zone(s) au total</p>
          }

          <!-- Zone tampon -->
          <button (click)="addZone('stamp')"
                  class="w-full px-3 py-2 rounded-lg font-medium border-2 border-dashed border-red-400 text-red-600 hover:bg-red-50 mt-1">
            {{ stampZoneCurrent() ? '✓ Redessiner zone tampon' : '+ Zone Tampon (optionnel)' }}
          </button>
          @if (stampZoneCurrent()) {
            <button (click)="removeStampZone()" class="w-full text-gray-400 hover:text-red-500">Effacer zone tampon</button>
          }

          <!-- Avertissement workflow -->
          @if (signataires().length > 0 && !allSignersHaveZone()) {
            <p class="text-[10px] text-amber-600 bg-amber-50 rounded px-2 py-1">
              ⚠ Chaque signataire devrait avoir une zone sur au moins une page.
            </p>
          }
        </div>

        <div class="p-4 border-t border-gray-200 space-y-2">
          <button (click)="saveZones()" [disabled]="!hasAnySigZone() || saving()"
                  class="w-full bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium disabled:opacity-40">
            {{ saving() ? 'Sauvegarde…' : 'Enregistrer les zones' }}
          </button>
          @if (saved()) { <p class="text-xs text-green-600 text-center">✓ Zones enregistrées</p> }
          @if (!hasAnySigZone()) { <p class="text-xs text-gray-400 text-center">Au moins une zone signature requise</p> }

          @if (docStatut() === 'RETOURNE') {
            <input #replacePdfInput type="file" accept=".pdf" class="hidden" (change)="onReplacePdfSelected($event)" />
            <button (click)="replacePdfInput.click()" [disabled]="replacing()"
                    class="w-full px-3 py-2 rounded-lg text-xs font-medium border border-orange-300 text-orange-600 hover:bg-orange-50 disabled:opacity-40">
              {{ replacing() ? 'Remplacement…' : '↑ Remplacer le PDF' }}
            </button>
            @if (replaced()) { <p class="text-xs text-green-600 text-center">✓ PDF remplacé</p> }
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

            <!-- Zones de signature existantes -->
            @if (pageLoaded()) {
              @for (z of sigZonesOnPage(currentPage()); track $index) {
                <div class="absolute border-2 border-dashed cursor-move select-none"
                     [style.left.px]="z.x / 100 * imgW()"
                     [style.top.px]="z.y / 100 * imgH()"
                     [style.width.px]="z.w / 100 * imgW()"
                     [style.height.px]="z.h / 100 * imgH()"
                     [style.border-color]="zoneColor(z.userId)"
                     [style.background]="zoneColor(z.userId) + '18'"
                     (mousedown)="startDragZone($event, z)">
                  <span class="absolute inset-0 flex items-center justify-center text-xs font-medium pointer-events-none"
                        [style.color]="zoneColor(z.userId)">
                    ✍ {{ z.userNom ?? 'Signature' }}
                  </span>
                </div>
              }

              <!-- Zone tampon -->
              @if (stampZoneCurrent()) {
                <div class="absolute border-2 border-dashed border-red-500 bg-red-50/30 cursor-move select-none"
                     [style.left.px]="stampZoneCurrent()!.x / 100 * imgW()"
                     [style.top.px]="stampZoneCurrent()!.y / 100 * imgH()"
                     [style.width.px]="stampZoneCurrent()!.w / 100 * imgW()"
                     [style.height.px]="stampZoneCurrent()!.h / 100 * imgH()"
                     (mousedown)="startDragStamp($event)">
                  <span class="absolute inset-0 flex items-center justify-center text-xs font-medium text-red-600 pointer-events-none">🔴 Tampon</span>
                </div>
              }
            }

            <!-- Dessin en cours -->
            @if (drawRect()) {
              <div class="absolute border-2 border-dashed pointer-events-none"
                   [class]="placingMode() === 'stamp' ? 'border-red-500 bg-red-50/30' : 'border-blue-500 bg-blue-50/30'"
                   [style.left.px]="drawRect()!.x / 100 * imgW()"
                   [style.top.px]="drawRect()!.y / 100 * imgH()"
                   [style.width.px]="drawRect()!.w / 100 * imgW()"
                   [style.height.px]="drawRect()!.h / 100 * imgH()">
              </div>
            }

            <!-- Overlay dessin (couvre tout, activé quand placingMode) -->
            @if (placingMode()) {
              <div class="absolute inset-0 cursor-crosshair"
                   (mousedown)="startDraw($event)"
                   (mousemove)="onDraw($event)"
                   (mouseup)="endDraw($event)">
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
  @ViewChild('pageWrapper')     pageWrapper!:     ElementRef<HTMLDivElement>;
  @ViewChild('pageImg')         pageImg!:         ElementRef<HTMLImageElement>;
  @ViewChild('replacePdfInput') replacePdfInput!: ElementRef<HTMLInputElement>;

  private api    = inject(ApiService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  private docId = '';
  docTitre     = signal('');
  docStatut    = signal<string>('BROUILLON');
  renvoyeMotif = signal<string | null>(null);
  pageCount    = signal(0);
  pages        = signal<number[]>([]);
  thumbUrls    = signal<Record<number, string>>({});
  currentPage  = signal(0);
  pageUrl      = signal<string | null>(null);
  pageLoaded   = signal(false);
  imgW         = signal(0);
  imgH         = signal(0);

  /** Signataires du workflow (vide = legacy) */
  signataires = signal<Signataire[]>([]);
  /** Signataire sélectionné dans le picker (userId ou '' pour auto/premier) */
  activeSignerUserId = '';

  /** Zones signature : flat array, une par signataire par page */
  signatureZones = signal<ZoneEntry[]>([]);
  /** Zone tampon : une par page max */
  stampZones = signal<Record<number, ZoneXY>>({});

  // ── Computed ─────────────────────────────────────────────────────────────

  stampZoneCurrent = computed(() => this.stampZones()[this.currentPage()] ?? null);
  hasAnySigZone    = computed(() => this.signatureZones().length > 0);
  totalSigZones    = computed(() => this.signatureZones().length);

  sigZonesOnPage(page: number): ZoneEntry[] {
    return this.signatureZones().filter(z => z.page === page);
  }

  allSignersHaveZone(): boolean {
    return this.signataires().every(s =>
      this.signatureZones().some(z => z.userId === s.userId));
  }

  /** Couleur par userId — legacy = bleu fixe */
  zoneColor(userId?: string): string {
    if (!userId) return '#3B82F6';
    const idx = this.signataires().findIndex(s => s.userId === userId);
    return SIGNER_COLORS[idx >= 0 ? idx % SIGNER_COLORS.length : 0];
  }

  // ── Dessin ────────────────────────────────────────────────────────────────

  placingMode = signal<'signature' | 'stamp' | null>(null);
  drawRect    = signal<ZoneXY | null>(null);
  private drawStart: { x: number; y: number } | null = null;

  saving    = signal(false);
  saved     = signal(false);
  replacing = signal(false);
  replaced  = signal(false);
  private blobUrls: string[] = [];

  // ── Init ─────────────────────────────────────────────────────────────────

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
        this.signatureZones.set(doc.signatureZones as ZoneEntry[]);
      }
      if (doc.stampZones?.length) {
        const m: Record<number, ZoneXY> = {};
        (doc.stampZones as ZoneEntry[]).forEach((z: ZoneEntry) => {
          m[z.page] = { x: z.x, y: z.y, w: z.w, h: z.h };
        });
        this.stampZones.set(m);
      }
      this.loadPage(0);
      this.loadThumbs();
    });

    this.api.getDocumentWorkflowSignataires(this.docId).subscribe(sigs => {
      this.signataires.set(sigs ?? []);
      // Pré-sélectionner le premier signataire si un seul
      if (sigs?.length === 1) this.activeSignerUserId = sigs[0].userId;
    });
  }

  ngOnDestroy() { this.blobUrls.forEach(u => URL.revokeObjectURL(u)); }

  back() { this.router.navigate(['/bureau']); }

  private loadPage(p: number) {
    this.currentPage.set(p);
    this.pageUrl.set(null);
    this.pageLoaded.set(false);
    this.imgW.set(0); this.imgH.set(0);
    this.api.getBureauPage(this.docId, p).subscribe(url => {
      this.blobUrls.push(url); this.pageUrl.set(url);
    });
  }

  private loadThumbs() {
    for (let p = 0; p < this.pageCount(); p++) {
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

  removeZone(zone: ZoneEntry) {
    this.signatureZones.update(list =>
      list.filter(z => !(z.page === zone.page && z.userId === zone.userId))
    );
    this.saved.set(false);
  }

  removeStampZone() {
    const p = this.currentPage();
    this.stampZones.update(m => { const n = { ...m }; delete n[p]; return n; });
    this.saved.set(false);
  }

  copyToAllPages() {
    const zonesOnCurrent = this.sigZonesOnPage(this.currentPage());
    if (!zonesOnCurrent.length) return;
    this.signatureZones.update(list => {
      // Retirer les zones de toutes les autres pages qui ont le même userId
      const userIds = new Set(zonesOnCurrent.map(z => z.userId));
      const withoutOtherPages = list.filter(z =>
        z.page === this.currentPage() || !userIds.has(z.userId));
      // Copier pour chaque page
      const copies: ZoneEntry[] = [];
      for (let p = 0; p < this.pageCount(); p++) {
        if (p === this.currentPage()) continue;
        zonesOnCurrent.forEach(z => copies.push({ ...z, page: p }));
      }
      return [...withoutOtherPages, ...copies];
    });
    this.saved.set(false);
  }

  // ── Dessin ────────────────────────────────────────────────────────────────

  startDraw(ev: MouseEvent) {
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    this.drawStart = {
      x: (ev.clientX - rect.left) / rect.width  * 100,
      y: (ev.clientY - rect.top)  / rect.height * 100,
    };
    ev.preventDefault();
  }

  onDraw(ev: MouseEvent) {
    if (!this.drawStart) return;
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const cx = (ev.clientX - rect.left) / rect.width  * 100;
    const cy = (ev.clientY - rect.top)  / rect.height * 100;
    this.drawRect.set({
      x: Math.min(this.drawStart.x, cx),
      y: Math.min(this.drawStart.y, cy),
      w: Math.abs(cx - this.drawStart.x),
      h: Math.abs(cy - this.drawStart.y),
    });
    ev.preventDefault();
  }

  endDraw(ev: MouseEvent) {
    const dr = this.drawRect();
    const mode = this.placingMode();
    if (!this.drawStart || !mode || !dr || dr.w < 2 || dr.h < 1) {
      this.resetDraw(); return;
    }
    const w = Math.min(dr.w, 100);
    const h = Math.min(dr.h, 100);
    const x = Math.max(0, Math.min(dr.x, 100 - w));
    const y = Math.max(0, Math.min(dr.y, 100 - h));
    const p = this.currentPage();

    if (mode === 'stamp') {
      this.stampZones.update(m => ({ ...m, [p]: { x, y, w, h } }));
    } else {
      // Résoudre le signataire actif
      const sigs = this.signataires();
      let userId: string | undefined;
      let userNom: string | undefined;

      if (sigs.length === 0) {
        // Legacy : zone anonyme
        userId = undefined; userNom = undefined;
      } else if (this.activeSignerUserId) {
        // Signataire sélectionné dans le picker
        const s = sigs.find(s => s.userId === this.activeSignerUserId);
        userId = s?.userId; userNom = s?.userNom;
      } else {
        // Auto : premier signataire sans zone sur cette page
        const signerWithoutZone = sigs.find(s =>
          !this.signatureZones().some(z => z.page === p && z.userId === s.userId));
        const target = signerWithoutZone ?? sigs[0];
        userId = target.userId; userNom = target.userNom;
      }

      // Remplacer la zone du même signataire sur cette page
      this.signatureZones.update(list => {
        const without = list.filter(z =>
          !(z.page === p && z.userId === userId));
        return [...without, { page: p, x, y, w, h, userId, userNom }];
      });
    }

    this.resetDraw();
    this.saved.set(false);
    ev.preventDefault();
  }

  private resetDraw() {
    this.placingMode.set(null);
    this.drawRect.set(null);
    this.drawStart = null;
  }

  // ── Drag zone existante ───────────────────────────────────────────────────

  private draggingZone: ZoneEntry | null = null;
  private draggingStamp = false;
  private dragOffset = { dx: 0, dy: 0 };
  private boundMove = this.onGlobalMove.bind(this);
  private boundUp   = this.onGlobalUp.bind(this);

  startDragZone(ev: MouseEvent, zone: ZoneEntry) {
    if (this.placingMode()) return;
    this.draggingZone = zone;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    this.dragOffset = {
      dx: (ev.clientX - rect.left) / rect.width  * 100 - zone.x,
      dy: (ev.clientY - rect.top)  / rect.height * 100 - zone.y,
    };
    document.addEventListener('mousemove', this.boundMove);
    document.addEventListener('mouseup',   this.boundUp);
    ev.preventDefault();
  }

  startDragStamp(ev: MouseEvent) {
    const zone = this.stampZoneCurrent();
    if (!zone || this.placingMode()) return;
    this.draggingStamp = true;
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    this.dragOffset = {
      dx: (ev.clientX - rect.left) / rect.width  * 100 - zone.x,
      dy: (ev.clientY - rect.top)  / rect.height * 100 - zone.y,
    };
    document.addEventListener('mousemove', this.boundMove);
    document.addEventListener('mouseup',   this.boundUp);
    ev.preventDefault();
  }

  private onGlobalMove(ev: MouseEvent) {
    const rect = this.pageWrapper.nativeElement.getBoundingClientRect();
    const cx = (ev.clientX - rect.left) / rect.width  * 100;
    const cy = (ev.clientY - rect.top)  / rect.height * 100;

    if (this.draggingZone) {
      const z = this.draggingZone;
      const nx = Math.max(0, Math.min(cx - this.dragOffset.dx, 100 - z.w));
      const ny = Math.max(0, Math.min(cy - this.dragOffset.dy, 100 - z.h));
      this.signatureZones.update(list => list.map(entry =>
        entry.page === z.page && entry.userId === z.userId
          ? { ...entry, x: nx, y: ny }
          : entry
      ));
    } else if (this.draggingStamp) {
      const p = this.currentPage();
      const zone = this.stampZones()[p];
      if (!zone) return;
      this.stampZones.update(m => ({
        ...m,
        [p]: {
          ...zone,
          x: Math.max(0, Math.min(cx - this.dragOffset.dx, 100 - zone.w)),
          y: Math.max(0, Math.min(cy - this.dragOffset.dy, 100 - zone.h)),
        }
      }));
    }
  }

  private onGlobalUp() {
    this.draggingZone  = null;
    this.draggingStamp = false;
    this.saved.set(false);
    document.removeEventListener('mousemove', this.boundMove);
    document.removeEventListener('mouseup',   this.boundUp);
  }

  // ── Remplacement PDF ──────────────────────────────────────────────────────

  onReplacePdfSelected(ev: Event) {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.replacing.set(true);
    this.api.replaceBureauPdf(this.docId, file).subscribe({
      next: (doc: any) => {
        this.replacing.set(false);
        this.replaced.set(true);
        this.replacePdfInput.nativeElement.value = '';
        const n = doc.pageCount ?? 1;
        this.pageCount.set(n);
        this.pages.set(Array.from({ length: n }, (_, i) => i));
        this.blobUrls.forEach(u => URL.revokeObjectURL(u));
        this.blobUrls = [];
        this.thumbUrls.set({});
        this.loadPage(0);
        this.loadThumbs();
      },
      error: () => this.replacing.set(false),
    });
  }

  // ── Sauvegarde ────────────────────────────────────────────────────────────

  saveZones() {
    if (!this.hasAnySigZone()) return;
    this.saving.set(true);
    const stArray = Object.entries(this.stampZones())
      .map(([page, z]) => ({ page: +page, ...z }));
    this.api.saveBureauZones(this.docId, {
      signatureZones: this.signatureZones(),
      stampZones: stArray,
    }).subscribe({
      next: () => { this.saving.set(false); this.saved.set(true); },
      error: () => this.saving.set(false),
    });
  }
}
