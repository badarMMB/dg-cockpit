import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-notes-de-service',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="h-full flex flex-col bg-gray-50">

      <!-- Header -->
      <header class="bg-white border-b border-gray-200 px-6 md:px-10 py-5 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-blue-100 flex items-center justify-center text-xl">📋</div>
          <div>
            <h1 class="text-xl font-bold text-gray-900">Notes de Service</h1>
            <p class="text-xs text-gray-500 mt-0.5">Documents officiels publiés par la Direction Générale</p>
          </div>
        </div>
      </header>

      <!-- Loader -->
      <div *ngIf="loading()" class="flex justify-center items-center flex-1">
        <div class="w-8 h-8 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
      </div>

      <!-- Liste -->
      <main *ngIf="!loading()" class="flex-1 overflow-y-auto p-5 md:p-8">

        <!-- Empty state -->
        <div *ngIf="notes().length === 0"
             class="flex flex-col items-center justify-center py-20 text-center">
          <div class="w-16 h-16 rounded-2xl bg-gray-100 flex items-center justify-center text-3xl mb-4">📋</div>
          <p class="text-gray-600 font-medium text-sm">Aucune note de service publiée</p>
          <p class="text-gray-400 text-xs mt-1">Les notes signées par le DG apparaîtront ici</p>
        </div>

        <!-- Feed chronologique -->
        <div *ngIf="notes().length > 0" class="max-w-3xl mx-auto space-y-4">
          <div *ngFor="let note of notes()"
               class="bg-white rounded-xl border border-gray-200 shadow-sm hover:shadow-md transition-shadow p-5">
            <div class="flex items-start justify-between gap-4">

              <div class="flex items-start gap-4 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-blue-50 flex items-center justify-center text-xl flex-shrink-0">📄</div>
                <div class="min-w-0">
                  <h3 class="font-semibold text-gray-900 text-sm leading-snug truncate">{{ note.title }}</h3>
                  <p class="text-xs text-gray-500 mt-1">Publiée le {{ formatDate(note.signedAt) }}</p>
                  <div class="flex items-center gap-2 mt-2">
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-100 text-blue-700">
                      Note de service
                    </span>
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-green-100 text-green-700">
                      Publiée
                    </span>
                  </div>
                </div>
              </div>

              <button (click)="lire(note)"
                      class="flex-shrink-0 px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors whitespace-nowrap">
                Lire →
              </button>
            </div>
          </div>
        </div>

      </main>
    </div>
  `
})
export class NotesDeServiceComponent implements OnInit {
  private api    = inject(ApiService);
  private router = inject(Router);

  notes   = signal<any[]>([]);
  loading = signal(true);

  ngOnInit() {
    this.api.getNotesDeService().subscribe({
      next: data => { this.notes.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  lire(note: any) {
    this.router.navigate(['/pdf-viewer', note.id]);
  }

  formatDate(s: string): string {
    if (!s) return '—';
    return new Date(s).toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
  }
}
