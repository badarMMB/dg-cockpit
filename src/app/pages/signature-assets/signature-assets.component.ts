import { Component, signal, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

interface SignatureAsset {
  id: string;
  assetType: 'SIGNATURE' | 'STAMP';
  originalFileName: string;
  fileSize: number;
  createdAt: string;
  bucket: string;
  objectKey: string;
  url?: string;
}

@Component({
  selector: 'app-signature-assets',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex items-center justify-between mb-4">
        <div></div>
        <button (click)="showUpload = true"
                class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 text-sm font-medium">
          + Ajouter
        </button>
      </div>

      <!-- Upload modal -->
      @if (showUpload) {
        <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md">
            <h2 class="text-lg font-semibold mb-4">Ajouter un actif</h2>

            <div class="mb-4">
              <label class="block text-sm font-medium text-gray-700 mb-1">Type</label>
              <select [(ngModel)]="uploadType"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                <option value="SIGNATURE">Signature</option>
                <option value="STAMP">Cachet</option>
              </select>
            </div>

            <div class="mb-4">
              <label class="block text-sm font-medium text-gray-700 mb-1">Image (PNG, JPG)</label>
              <input type="file" accept="image/*" (change)="onFileSelected($event)"
                     class="w-full text-sm text-gray-600" />
            </div>

            @if (uploadPreview) {
              <div class="mb-4 border rounded-lg p-2 bg-gray-50 flex items-center justify-center">
                <img [src]="uploadPreview" alt="preview" class="max-h-32 object-contain" />
              </div>
            }

            <div class="flex justify-end gap-3">
              <button (click)="cancelUpload()"
                      class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
              <button (click)="doUpload()" [disabled]="!selectedFile || uploading()"
                      class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm disabled:opacity-50">
                {{ uploading() ? 'Traitement…' : 'Enregistrer' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Tab filter -->
      <div class="flex gap-2 mb-4">
        @for (tab of tabs; track tab.value) {
          <button (click)="activeTab.set(tab.value)"
                  [class]="activeTab() === tab.value
                    ? 'px-4 py-1.5 rounded-full text-sm font-medium bg-blue-600 text-white'
                    : 'px-4 py-1.5 rounded-full text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200'">
            {{ tab.label }}
          </button>
        }
      </div>

      <!-- Grid -->
      @if (loading()) {
        <p class="text-gray-400 text-sm">Chargement…</p>
      } @else if (filtered().length === 0) {
        <p class="text-gray-400 text-sm">Aucun actif enregistré.</p>
      } @else {
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          @for (asset of filtered(); track asset.id) {
            <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm hover:shadow-md transition">
              <div class="bg-gray-50 flex items-center justify-center h-32 p-2">
                @if (asset.url) {
                  <img [src]="asset.url" [alt]="asset.originalFileName"
                       class="max-h-full max-w-full object-contain" />
                } @else {
                  <div class="text-gray-300 text-xs">Chargement…</div>
                }
              </div>
              <div class="p-3">
                <p class="text-xs font-medium text-gray-700 truncate">{{ asset.originalFileName }}</p>
                <p class="text-xs text-gray-400 mt-0.5">
                  {{ asset.assetType === 'SIGNATURE' ? 'Signature' : 'Cachet' }}
                </p>
                <button (click)="remove(asset)"
                        class="mt-2 w-full text-xs text-red-500 hover:text-red-700 text-left">
                  Supprimer
                </button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class SignatureAssetsComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private blobUrls: string[] = [];

  assets = signal<SignatureAsset[]>([]);
  loading = signal(false);
  uploading = signal(false);

  showUpload = false;
  uploadType = 'SIGNATURE';
  selectedFile: File | null = null;
  uploadPreview: string | null = null;

  activeTab = signal<'ALL' | 'SIGNATURE' | 'STAMP'>('ALL');

  tabs = [
    { value: 'ALL' as const, label: 'Tous' },
    { value: 'SIGNATURE' as const, label: 'Signatures' },
    { value: 'STAMP' as const, label: 'Cachets' },
  ];

  filtered = () => {
    const tab = this.activeTab();
    return tab === 'ALL' ? this.assets() : this.assets().filter(a => a.assetType === tab);
  };

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getSignatureAssets().subscribe({
      next: (list: any[]) => {
        this.blobUrls.forEach(u => URL.revokeObjectURL(u));
        this.blobUrls = [];
        const withUrls = list.map(a => ({ ...a, url: '' }));
        this.assets.set(withUrls);
        this.loading.set(false);
        list.forEach((a, i) => {
          this.api.getSignatureImageBlob(a.id).subscribe(url => {
            this.blobUrls.push(url);
            this.assets.update(arr => arr.map((x, idx) => idx === i ? { ...x, url } : x));
          });
        });
      },
      error: () => this.loading.set(false)
    });
  }

  ngOnDestroy() {
    this.blobUrls.forEach(u => URL.revokeObjectURL(u));
  }

  onFileSelected(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.selectedFile = file;
    const reader = new FileReader();
    reader.onload = e => this.uploadPreview = e.target?.result as string;
    reader.readAsDataURL(file);
  }

  doUpload() {
    if (!this.selectedFile) return;
    this.uploading.set(true);
    this.api.uploadSignatureAsset(this.uploadType, this.selectedFile).subscribe({
      next: (asset: any) => {
        this.uploading.set(false);
        this.cancelUpload();
        this.api.getSignatureImageBlob(asset.id).subscribe(url => {
          this.blobUrls.push(url);
          this.assets.update(list => [{ ...asset, url }, ...list]);
        });
      },
      error: () => this.uploading.set(false)
    });
  }

  cancelUpload() {
    this.showUpload = false;
    this.selectedFile = null;
    this.uploadPreview = null;
  }

  remove(asset: SignatureAsset) {
    if (!confirm('Supprimer cet actif ?')) return;
    this.api.deleteSignatureAsset(asset.id).subscribe({
      next: () => this.assets.update(list => list.filter(a => a.id !== asset.id))
    });
  }
}
