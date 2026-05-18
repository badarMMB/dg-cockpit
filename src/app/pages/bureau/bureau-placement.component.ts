import { Component, OnInit, OnDestroy, signal, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface Zone {
  page: number;
  x: number; y: number; w: number; h: number;
}

@Component({
  selector: 'app-bureau-placement',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex h-screen bg-gray-100 overflow-hidden">

      <!-- Panneau gauche -->
      <div class="w-64 bg-white border-r border-gray-200 flex flex-col">
        <div class="p-4 border-b border-gray-200">
          <button (click)="back()" class="text-sm text-gray-500 hover:text-gray-800 flex items-center gap-1">
            ← Retour au Bureau
          </button>
          <h2 class="text-sm font-semibold text-gray-800 mt-3 truncate">{{ docTitre() }}</h2>
          <p class="text-xs text-gray-400 mt-0.5">{{ pageCount() }} page(s)</p>
        </div>

        <!-- Thumbnails pages -->
        <div class="flex-1 overflow-y-auto p-3 space-y-2">
          @for (p of pages(); track p) {
            <button (click)="goToPage(p)"
                    [class]="currentPage() === p
                      ? 'w-full border-2 border-blue-500 rounded-lg overflow-hidden'
                      : 'w-full border-2 border-transparent rounded-lg overflow-hidden hover:border-gray-300'">
              @if (thumbUrls()[p]) {
                <img [src]="thumbUrls()[p]" class="w-full" />
              } @else {
                <div class="bg-gray-100 h-24 flex items-center justify-center text-xs text-gray-400">
                  Page {{ p + 1 }}
                </div>
              }
            </button>
          }
        </div>

        <!-- Actions zones -->
        <div class="p-4 border-t border-gray-200 space-y-2">
          <button (click)="addZone('signature')"
                  [disabled]="!!signatureZone()"
                  class="w-full px-3 py-2 rounded-lg text-xs font-medium border-2 border-dashed border-blue-400 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed">
            {{ signatureZone() ? '✓ Zone Signature posée' : '+ Zone Signature DG' }}
          </button>
          <button (click)="addZone('stamp')"
                  [disabled]="!!stampZone()"
                  class="w-full px-3 py-2 rounded-lg text-xs font-medium border-2 border-dashed border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed">
            {{ stampZone() ? '✓ Zone Tampon posée' : '+ Zone Tampon' }}
          </button>
          @if (signatureZone()) {
            <button (click)="removeZone('signature')"
                    class="w-full text-xs text-gray-400 hover:text-red-500">
              Effacer zone signature
            </button>
          }
          @if (stampZone()) {
            <button (click)="removeZone('stamp')"
                    class="w-full text-xs text-gray-400 hover:text-red-500">
              Effacer zone tampon
            </button>
          }
        </div>

        <div class="p-4 border-t border-gray-200">
          <button (click)="saveZones()" [disabled]="!signatureZone() || saving()"
                  class="w-full bg-blue-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium disabled:opacity-40">
            {{ saving() ? 'Sauvegarde…' : 'Enregistrer les zones' }}
          </button>
          @if (saved()) {
            <p class="text-xs text-green-600 text-center mt-2">✓ Zones enregistrées</p>
          }
        </div>
      </div>

      <!-- Viewer principal -->
      <div class="flex-1 overflow-auto flex items-start justify-center p-8">
        <div class="relative inline-block shadow-xl" #pageWrapper>
          @if (pageUrl()) {
            <img [src]="pageUrl()" class="max-w-full block" (load)="onImageLoaded()" #pageImg />

            <!-- Zone signature (bleue) -->
            @if (signatureZone() && signatureZone()!.page === currentPage()) {
              <div class="absolute border-2 border-dashed border-blue-500 bg-blue-50/30 cursor-move flex items-center justify-center"
                   [style.left.%]="signatureZone()!.x"
                   [style.top.%]="signatureZone()!.y"
                   [style.width.%]="signatureZone()!.w"
                   [style.height.%]="signatureZone()!.h"
                   (mousedown)="startDrag($event, 'signature')">
                <span class="text-blue-600 text-xs font-medium select-none pointer-events-none">✍️ Signature DG</span>
              </div>
            }

            <!-- Zone tampon (rouge) -->
            @if (stampZone() && stampZone()!.page === currentPage()) {
              <div class="absolute border-2 border-dashed border-red-500 bg-red-50/30 cursor-move flex items-center justify-center"
                   [style.left.%]="stampZone()!.x"
                   [style.top.%]="stampZone()!.y"
                   [style.width.%]="stampZone()!.w"
                   [style.height.%]="stampZone()!.h"
                   (mousedown)="startDrag($event, 'stamp')">
                <span class="text-red-600 text-xs font-medium select-none pointer-events-none">🔴 Tampon</span>
              </div>
            }

            <!-- Mode placement actif -->
            @if (placingMode()) {
              <div class="absolute inset-0 cursor-crosshair"
                   (mousedown)="startDraw($event)"
                   (mousemove)="onDraw($event)"
                   (mouseup)="endDraw($event)">
                @if (drawRect()) {
                  <div class="absolute border-2 border-dashed pointer-events-none"
                       [class]="placingMode() === 'signature' ? 'border-blue-500 bg-blue-50/30' : 'border-red-500 bg-red-50/30'"
                       [style.left.%]="drawRect()!.x"
                       [style.top.%]="drawRect()!.y"
                       [style.width.%]="drawRect()!.w"
                       [style.height.%]="drawRect()!.h">
                  </div>
                }
              </div>
            }
          } @else {
            <div class="w-96 h-[560px] bg-gray-200 flex items-center justify-center text-gray-400">
              Chargement…
            </div>
          }
        </div>
      </div>
    </div>
  `
})
export class BureauPlacementComponent implements OnInit, OnDestroy {
  @ViewChild('pageWrapper') pageWrapper!: ElementRef<HTMLDivElement>;
  @ViewChild('pageImg')     pageImg!:     ElementRef<HTMLImageElement>;

  private api    = inject(ApiService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  private docId = '';
  docTitre  = signal('');
  pageCount = signal(0);
  pages     = signal<number[]>([]);
  thumbUrls = signal<Record<number, string>>({});
  currentPage = signal(0);
  pageUrl     = signal<string | null>(null);
  pageLoaded  = signal(false);

  signatureZone = signal<Zone | null>(null);
  stampZone     = signal<Zone | null>(null);

  placingMode = signal<'signature' | 'stamp' | null>(null);
  drawRect    = signal<{ x: number; y: number; w: number; h: number } | null>(null);
  private drawStart: { x: number; y: number } | null = null;

  saving = signal(false);
  saved  = signal(false);

  private blobUrls: string[] = [];

  ngOnInit() {
    this.docId = this.route.snapshot.paramMap.get('id') ?? '';
    this.api.getBureauDocuments().subscribe(list => {
      const doc = list.find((d: any) => d.id === this.docId);
      if (!doc) { this.router.navigate(['/bureau']); return; }
      this.docTitre.set(doc.titre);
      this.pageCount.set(doc.pageCount ?? 1);
      this.pages.set(Array.from({ length: doc.pageCount ?? 1 }, (_, i) => i));
      if (doc.signatureZone) this.signatureZone.set(doc.signatureZone as Zone);
      if (doc.stampZone)     this.stampZone.set(doc.stampZone as Zone);
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

  onImageLoaded() { this.pageLoaded.set(true); }

  // ── Mode dessin zones ─────────────────────────────────────────────────────

  addZone(type: 'signature' | 'stamp') {
    this.placingMode.set(type);
    this.drawRect.set(null);
  }

  removeZone(type: 'signature' | 'stamp') {
    if (type === 'signature') this.signatureZone.set(null);
    else                      this.stampZone.set(null);
    this.saved.set(false);
  }

  startDraw(ev: MouseEvent) {
    if (!this.placingMode()) return;
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const pw = rect.width;
    const ph = rect.height;
    this.drawStart = {
      x: ((ev.clientX - rect.left) / pw) * 100,
      y: ((ev.clientY - rect.top)  / ph) * 100,
    };
    ev.preventDefault();
  }

  onDraw(ev: MouseEvent) {
    if (!this.drawStart || !this.placingMode()) return;
    const el = (ev.currentTarget as HTMLElement);
    const rect = el.getBoundingClientRect();
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
      const zone: Zone = { page: this.currentPage(), ...dr };
      if (this.placingMode() === 'signature') this.signatureZone.set(zone);
      else                                     this.stampZone.set(zone);
    }
    this.placingMode.set(null);
    this.drawRect.set(null);
    this.drawStart = null;
    this.saved.set(false);
    ev.preventDefault();
  }

  // ── Drag pour déplacer une zone existante ────────────────────────────────

  private dragType: 'signature' | 'stamp' | null = null;
  private dragOffset: { dx: number; dy: number } = { dx: 0, dy: 0 };
  private boundMouseMove = this.onGlobalMove.bind(this);
  private boundMouseUp   = this.onGlobalUp.bind(this);

  startDrag(ev: MouseEvent, type: 'signature' | 'stamp') {
    if (this.placingMode()) return;
    this.dragType = type;
    const zone = type === 'signature' ? this.signatureZone() : this.stampZone();
    if (!zone) return;
    const wrapper = this.pageWrapper.nativeElement;
    const rect = wrapper.getBoundingClientRect();
    const cx = ((ev.clientX - rect.left) / rect.width)  * 100;
    const cy = ((ev.clientY - rect.top)  / rect.height) * 100;
    this.dragOffset = { dx: cx - zone.x, dy: cy - zone.y };
    document.addEventListener('mousemove', this.boundMouseMove);
    document.addEventListener('mouseup',   this.boundMouseUp);
    ev.preventDefault();
  }

  private onGlobalMove(ev: MouseEvent) {
    if (!this.dragType) return;
    const wrapper = this.pageWrapper.nativeElement;
    const rect = wrapper.getBoundingClientRect();
    const cx = ((ev.clientX - rect.left) / rect.width)  * 100;
    const cy = ((ev.clientY - rect.top)  / rect.height) * 100;
    const zone = this.dragType === 'signature' ? this.signatureZone() : this.stampZone();
    if (!zone) return;
    const updated: Zone = { ...zone, x: cx - this.dragOffset.dx, y: cy - this.dragOffset.dy };
    if (this.dragType === 'signature') this.signatureZone.set(updated);
    else                               this.stampZone.set(updated);
  }

  private onGlobalUp(_ev: MouseEvent) {
    this.dragType = null;
    this.saved.set(false);
    document.removeEventListener('mousemove', this.boundMouseMove);
    document.removeEventListener('mouseup',   this.boundMouseUp);
  }

  // ── Sauvegarde ───────────────────────────────────────────────────────────

  saveZones() {
    if (!this.signatureZone()) return;
    this.saving.set(true);
    const payload: any = { signatureZone: this.signatureZone() };
    if (this.stampZone()) payload.stampZone = this.stampZone();
    this.api.saveBureauZones(this.docId, payload).subscribe({
      next: () => { this.saving.set(false); this.saved.set(true); },
      error: () => this.saving.set(false),
    });
  }
}
