import { Component, Input, OnInit, OnDestroy, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-pdf-file-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex h-full bg-gray-100 overflow-hidden rounded-b-xl">

      <!-- Panneau vignettes -->
      <div class="w-44 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
        <div class="p-3 border-b border-gray-100">
          <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide truncate">{{ fileName }}</p>
          <p class="text-[10px] text-gray-400 mt-0.5">{{ pages().length }} page{{ pages().length > 1 ? 's' : '' }}</p>
        </div>
        <div class="flex-1 overflow-y-auto p-2 space-y-2">
          <div *ngFor="let p of pages()"
               (click)="goToPage(p)"
               class="border rounded-lg overflow-hidden cursor-pointer transition-colors"
               [ngClass]="currentPage() === p
                 ? 'border-primary border-2 shadow-sm'
                 : 'border-gray-200 hover:border-primary/50'">
            <img *ngIf="thumbUrls()[p]" [src]="thumbUrls()[p]" class="w-full object-contain block" alt="Page {{ p + 1 }}" />
            <div *ngIf="!thumbUrls()[p]" class="bg-gray-50 h-14 flex items-center justify-center text-xs text-gray-400">
              {{ p + 1 }}
            </div>
            <p class="text-center text-[10px] text-gray-500 py-0.5">{{ p + 1 }}</p>
          </div>
        </div>
      </div>

      <!-- Zone principale -->
      <div class="flex-1 flex flex-col overflow-hidden">

        <!-- Toolbar -->
        <div class="bg-white border-b border-gray-200 px-4 py-2 flex items-center gap-3">
          <button (click)="prevPage()" [disabled]="currentPage() === 0"
                  class="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30 transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>
          <span class="text-sm text-gray-600 font-medium">
            Page {{ currentPage() + 1 }} / {{ pages().length }}
          </span>
          <button (click)="nextPage()" [disabled]="currentPage() === pages().length - 1"
                  class="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30 transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
            </svg>
          </button>
          <div class="flex-1"></div>
          <span *ngIf="loading()" class="text-xs text-gray-400 flex items-center gap-1.5">
            <span class="w-3.5 h-3.5 border-2 border-gray-300 border-t-primary rounded-full animate-spin inline-block"></span>
            Chargement…
          </span>
        </div>

        <!-- Page courante -->
        <div class="flex-1 overflow-auto flex items-start justify-center p-4 bg-gray-100">
          <img *ngIf="pageUrl()" [src]="pageUrl()!" class="shadow-xl max-w-full block" alt="Page {{ currentPage() + 1 }}" />
          <div *ngIf="!pageUrl() && !loading()"
               class="flex items-center justify-center h-full text-gray-400 text-sm">
            Aucun aperçu disponible
          </div>
          <div *ngIf="!pageUrl() && loading()"
               class="flex items-center justify-center h-full">
            <div class="w-8 h-8 border-4 border-gray-200 border-t-primary rounded-full animate-spin"></div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PdfFileViewerComponent implements OnInit, OnDestroy {
  @Input() fileName!: string;

  private api = inject(ApiService);

  pages       = signal<number[]>([]);
  currentPage = signal(0);
  thumbUrls   = signal<Record<number, string>>({});
  pageUrl     = signal<string | null>(null);
  loading     = signal(true);

  private blobUrls: string[] = [];

  ngOnInit() {
    this.api.getFileInfo(this.fileName).subscribe({
      next: info => {
        const count = info.pageCount ?? 1;
        this.pages.set(Array.from({ length: count }, (_, i) => i));
        this.goToPage(0);
        this.loadThumbs(count);
      },
      error: () => this.loading.set(false),
    });
  }

  ngOnDestroy() {
    this.blobUrls.forEach(u => URL.revokeObjectURL(u));
  }

  private loadThumbs(count: number) {
    for (let p = 0; p < count; p++) {
      this.api.renderFilePageBlob(this.fileName, p).subscribe(url => {
        this.blobUrls.push(url);
        this.thumbUrls.update(m => ({ ...m, [p]: url }));
      });
    }
  }

  goToPage(p: number) {
    this.currentPage.set(p);
    this.pageUrl.set(null);
    this.loading.set(true);
    this.api.renderFilePageBlob(this.fileName, p).subscribe(url => {
      this.blobUrls.push(url);
      this.pageUrl.set(url);
      this.loading.set(false);
    });
  }

  prevPage() {
    if (this.currentPage() > 0) this.goToPage(this.currentPage() - 1);
  }

  nextPage() {
    if (this.currentPage() < this.pages().length - 1) this.goToPage(this.currentPage() + 1);
  }
}
