import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-pdf-documents',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="p-6 max-w-5xl mx-auto">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-bold text-gray-800">Documents PDF</h1>
        <label class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 text-sm font-medium cursor-pointer">
          + Importer un PDF
          <input type="file" accept="application/pdf" class="hidden" (change)="onFileSelected($event)" />
        </label>
      </div>

      @if (uploading()) {
        <div class="mb-4 text-sm text-blue-600">Import en cours…</div>
      }

      @if (loading()) {
        <p class="text-gray-400 text-sm">Chargement…</p>
      } @else if (docs().length === 0) {
        <p class="text-gray-400 text-sm">Aucun document importé.</p>
      } @else {
        <div class="bg-white rounded-xl shadow-sm overflow-hidden">
          <table class="w-full text-sm">
            <thead class="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th class="px-4 py-3 text-left">Titre</th>
                <th class="px-4 py-3 text-left">Pages</th>
                <th class="px-4 py-3 text-left">Statut</th>
                <th class="px-4 py-3 text-left">Date</th>
                <th class="px-4 py-3 text-left">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              @for (doc of docs(); track doc.id) {
                <tr class="hover:bg-gray-50 transition">
                  <td class="px-4 py-3 font-medium text-gray-800">{{ doc.title }}</td>
                  <td class="px-4 py-3 text-gray-500">{{ doc.pageCount }}</td>
                  <td class="px-4 py-3">
                    <span [class]="doc.status === 'FINALIZED'
                      ? 'px-2 py-0.5 rounded-full text-xs bg-green-100 text-green-700'
                      : 'px-2 py-0.5 rounded-full text-xs bg-yellow-100 text-yellow-700'">
                      {{ doc.status === 'FINALIZED' ? 'Finalisé' : 'Brouillon' }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-gray-400 text-xs">{{ doc.createdAt | date:'dd/MM/yyyy' }}</td>
                  <td class="px-4 py-3 flex gap-2">
                    <button (click)="openViewer(doc.id)"
                            class="text-blue-600 hover:underline text-xs">Annoter</button>
                    @if (doc.status === 'FINALIZED') {
                      <a [href]="api.downloadFinalPdf(doc.id)" target="_blank"
                         class="text-green-600 hover:underline text-xs">Télécharger</a>
                    }
                    <button (click)="remove(doc.id)"
                            class="text-red-500 hover:underline text-xs">Supprimer</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class PdfDocumentsComponent implements OnInit {
  api = inject(ApiService);
  private router = inject(Router);

  docs = signal<any[]>([]);
  loading = signal(false);
  uploading = signal(false);

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getPdfDocuments().subscribe({
      next: (list: any[]) => { this.docs.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onFileSelected(ev: Event) {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.api.uploadPdfDocument(file).subscribe({
      next: (doc: any) => {
        this.docs.update(list => [doc, ...list]);
        this.uploading.set(false);
      },
      error: () => this.uploading.set(false)
    });
  }

  openViewer(id: string) {
    this.router.navigate(['/pdf-viewer', id]);
  }

  remove(id: string) {
    if (!confirm('Supprimer ce document ?')) return;
    this.api.deletePdfDocument(id).subscribe({
      next: () => this.docs.update(list => list.filter(d => d.id !== id))
    });
  }
}
