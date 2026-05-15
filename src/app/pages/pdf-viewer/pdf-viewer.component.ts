import {
  Component, signal, inject, OnInit, OnDestroy, AfterViewInit,
  ElementRef, ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import Konva from 'konva';

interface Annotation {
  id?: string;
  pageId: string;
  annotationType: string;
  signatureAssetId?: string;
  xPercent: number;
  yPercent: number;
  widthPercent: number;
  heightPercent: number;
}

@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex h-screen bg-gray-100 overflow-hidden">

      <!-- Left panel -->
      <div class="w-64 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
        <div class="p-4 border-b border-gray-200">
          <button (click)="back()" class="text-sm text-blue-600 hover:underline">&larr; Retour</button>
          <h2 class="font-semibold text-gray-800 mt-1 truncate text-sm">{{ doc()?.title || 'Document PDF' }}</h2>
        </div>

        <!-- Thumbnails -->
        <div class="flex-1 overflow-y-auto p-2 space-y-2">
          @for (p of pages(); track p) {
            <div (click)="goToPage(p)"
                 [class]="currentPage() === p
                   ? 'border-2 border-blue-500 rounded-lg overflow-hidden cursor-pointer'
                   : 'border border-gray-200 rounded-lg overflow-hidden cursor-pointer hover:border-blue-300'">
              <img [src]="api.renderPage(docId, p)" class="w-full object-contain" />
              <p class="text-center text-xs text-gray-500 py-1">{{ p + 1 }}</p>
            </div>
          }
        </div>

        <!-- Assets -->
        <div class="border-t border-gray-200 p-3">
          <p class="text-xs font-semibold text-gray-500 uppercase mb-2">Signatures &amp; Cachets</p>
          <div class="space-y-2 overflow-y-auto max-h-48">
            @for (asset of assets(); track asset.id) {
              <div draggable="true" (dragstart)="onDragStart($event, asset)"
                   class="flex items-center gap-2 p-2 rounded-lg border border-gray-200 cursor-grab hover:bg-gray-50 select-none">
                @if (asset.url) {
                  <img [src]="asset.url" class="w-12 h-8 object-contain flex-shrink-0" />
                }
                <span class="text-xs text-gray-600 truncate">{{ asset.originalFileName }}</span>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Main area -->
      <div class="flex-1 flex flex-col overflow-hidden">
        <!-- Toolbar -->
        <div class="bg-white border-b border-gray-200 px-4 py-2 flex items-center gap-3">
          <span class="text-sm text-gray-500">Page {{ currentPage() + 1 }} / {{ pages().length }}</span>
          <div class="flex-1"></div>
          @if (selectedId()) {
            <button (click)="deleteSelected()"
                    class="px-3 py-1 text-sm text-red-600 border border-red-300 rounded-lg hover:bg-red-50">
              Supprimer sélection
            </button>
          }
          <button (click)="finalize()" [disabled]="finalizing()"
                  class="px-4 py-1.5 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 disabled:opacity-50">
            {{ finalizing() ? 'Finalisation…' : 'Finaliser le document' }}
          </button>
          @if (doc()?.status === 'FINALIZED') {
            <a [href]="api.downloadFinalPdf(docId)" target="_blank"
               class="px-4 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
              Télécharger PDF final
            </a>
          }
        </div>

        <!-- Canvas -->
        <div class="flex-1 overflow-auto flex items-start justify-center p-6"
             (dragover)="$event.preventDefault()" (drop)="onDrop($event)">
          <div #konvaContainer class="shadow-lg"></div>
        </div>
      </div>
    </div>
  `
})
export class PdfViewerComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('konvaContainer') containerRef!: ElementRef<HTMLDivElement>;

  api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  docId = '';
  doc = signal<any>(null);
  pages = signal<number[]>([]);
  assets = signal<any[]>([]);
  currentPage = signal(0);
  selectedId = signal<string | null>(null);
  finalizing = signal(false);

  private stage!: Konva.Stage;
  private bgLayer!: Konva.Layer;
  private annotLayer!: Konva.Layer;
  private transformer!: Konva.Transformer;
  private draggedAsset: any = null;

  // konvaNodeId -> backendAnnotationId
  private nodeAnnotMap = new Map<string, string>();

  ngOnInit() {
    this.docId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadDoc();
    this.loadAssets();
  }

  ngAfterViewInit() {
    // stage init deferred until first page loads (needs image dimensions)
  }

  ngOnDestroy() {
    this.stage?.destroy();
  }

  back() { this.router.navigate(['/pdf-documents']); }

  // ── Stage ────────────────────────────────────────────────────────────────

  private initStage(width: number, height: number) {
    this.stage?.destroy();
    this.nodeAnnotMap.clear();

    this.stage = new Konva.Stage({ container: this.containerRef.nativeElement, width, height });

    this.bgLayer = new Konva.Layer({ listening: false });
    this.annotLayer = new Konva.Layer();
    this.stage.add(this.bgLayer);
    this.stage.add(this.annotLayer);

    this.transformer = new Konva.Transformer({
      keepRatio: false,
      enabledAnchors: ['top-left', 'top-right', 'bottom-left', 'bottom-right',
                       'middle-left', 'middle-right', 'top-center', 'bottom-center'],
      boundBoxFunc: (_, newBox) => (newBox.width < 20 || newBox.height < 20 ? _ : newBox),
    });
    this.annotLayer.add(this.transformer);

    // click on stage bg → deselect
    this.stage.on('click tap', (e) => {
      if (e.target === this.stage || !e.target.draggable()) {
        this.transformer.nodes([]);
        this.selectedId.set(null);
        this.annotLayer.batchDraw();
      }
    });
  }

  // ── Data ─────────────────────────────────────────────────────────────────

  loadDoc() {
    this.api.getPdfDocuments().subscribe((list: any[]) => {
      const d = list.find(x => x.id === this.docId);
      if (!d) return;
      this.doc.set(d);
      this.pages.set(Array.from({ length: d.pageCount }, (_, i) => i));
      this.goToPage(0);
    });
  }

  loadAssets() {
    this.api.getSignatureAssets().subscribe((list: any[]) => {
      this.assets.set(list.map(a => ({ ...a, url: this.api.getSignatureImageUrl(a.id) })));
    });
  }

  // ── Page rendering ───────────────────────────────────────────────────────

  goToPage(page: number) {
    this.currentPage.set(page);
    const url = this.api.renderPage(this.docId, page);
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const w = Math.min(800, window.innerWidth - 320);
      const h = img.height * (w / img.width);
      this.initStage(w, h);
      this.bgLayer.add(new Konva.Image({ image: img, x: 0, y: 0, width: w, height: h }));
      this.bgLayer.batchDraw();
      this.loadAnnotationsForPage(page);
    };
    img.src = url;
  }

  loadAnnotationsForPage(page: number) {
    const pageId = `${this.docId}::${page}`;
    this.api.getAnnotations(pageId).subscribe((list: any[]) => {
      list.forEach(ann => this.renderAnnotation(ann));
    });
  }

  renderAnnotation(ann: Annotation) {
    if (!ann.signatureAssetId) return;
    const asset = this.assets().find(a => a.id === ann.signatureAssetId);
    if (!asset?.url) return;

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const w = this.stage.width();
      const h = this.stage.height();
      const node = this.makeImageNode(img,
        (ann.xPercent / 100) * w, (ann.yPercent / 100) * h,
        (ann.widthPercent / 100) * w, (ann.heightPercent / 100) * h);
      if (ann.id) this.nodeAnnotMap.set(node.id(), ann.id);
      node.on('dragend transformend', () => this.syncAnnotation(node));
      this.annotLayer.add(node);
      this.annotLayer.batchDraw();
    };
    img.src = asset.url;
  }

  // ── Drag & drop from assets panel ────────────────────────────────────────

  onDragStart(ev: DragEvent, asset: any) {
    this.draggedAsset = asset;
    ev.dataTransfer?.setData('text/plain', asset.id);
  }

  onDrop(ev: DragEvent) {
    ev.preventDefault();
    if (!this.draggedAsset || !this.stage) return;

    const rect = this.containerRef.nativeElement.getBoundingClientRect();
    const dropX = ev.clientX - rect.left;
    const dropY = ev.clientY - rect.top;

    const asset = this.draggedAsset;
    this.draggedAsset = null;
    if (!asset.url) return;

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const stageW = this.stage.width();
      const stageH = this.stage.height();
      const displayW = Math.min(200, stageW * 0.25);
      const displayH = img.height * (displayW / img.width);
      const x = dropX - displayW / 2;
      const y = dropY - displayH / 2;

      const ann: Annotation = {
        pageId: `${this.docId}::${this.currentPage()}`,
        annotationType: asset.assetType,
        signatureAssetId: asset.id,
        xPercent: (x / stageW) * 100,
        yPercent: (y / stageH) * 100,
        widthPercent: (displayW / stageW) * 100,
        heightPercent: (displayH / stageH) * 100,
      };

      this.api.createAnnotation(ann).subscribe((saved: any) => {
        const node = this.makeImageNode(img, x, y, displayW, displayH);
        this.nodeAnnotMap.set(node.id(), saved.id);
        node.on('dragend transformend', () => this.syncAnnotation(node));
        this.annotLayer.add(node);
        this.transformer.nodes([node]);
        this.selectedId.set(saved.id);
        this.annotLayer.batchDraw();
      });
    };
    img.src = asset.url;
  }

  // ── Sync to backend after move/resize ────────────────────────────────────

  syncAnnotation(node: Konva.Image) {
    const annotId = this.nodeAnnotMap.get(node.id());
    if (!annotId) return;

    const stageW = this.stage.width();
    const stageH = this.stage.height();
    // scaleX/Y applied by Transformer — multiply into width/height then reset
    const w = node.width() * node.scaleX();
    const h = node.height() * node.scaleY();

    this.api.updateAnnotation(annotId, {
      xPercent: (node.x() / stageW) * 100,
      yPercent: (node.y() / stageH) * 100,
      widthPercent: (w / stageW) * 100,
      heightPercent: (h / stageH) * 100,
    }).subscribe();
  }

  // ── Delete selected ───────────────────────────────────────────────────────

  deleteSelected() {
    const nodes = this.transformer.nodes();
    if (!nodes.length) return;
    const node = nodes[0] as Konva.Image;
    const annotId = this.nodeAnnotMap.get(node.id());
    if (annotId) {
      this.api.deleteAnnotation(annotId).subscribe();
      this.nodeAnnotMap.delete(node.id());
    }
    this.transformer.nodes([]);
    node.destroy();
    this.annotLayer.batchDraw();
    this.selectedId.set(null);
  }

  // ── Finalize ─────────────────────────────────────────────────────────────

  finalize() {
    this.finalizing.set(true);
    this.api.finalizePdfDocument(this.docId).subscribe({
      next: (updated: any) => { this.doc.set(updated); this.finalizing.set(false); },
      error: () => this.finalizing.set(false),
    });
  }

  // ── Helper ───────────────────────────────────────────────────────────────

  private makeImageNode(img: HTMLImageElement, x: number, y: number, width: number, height: number): Konva.Image {
    const node = new Konva.Image({
      id: `node_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      image: img, x, y, width, height,
      draggable: true,
    });
    // select on click
    node.on('click tap', () => {
      this.transformer.nodes([node]);
      this.selectedId.set(this.nodeAnnotMap.get(node.id()) ?? null);
      this.annotLayer.batchDraw();
    });
    return node;
  }
}
