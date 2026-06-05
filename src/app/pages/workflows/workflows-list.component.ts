import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

type StatusFilter = 'ALL' | 'RUNNING' | 'COMPLETED' | 'CANCELLED';

const STATUS_LABELS: Record<string, string> = {
  RUNNING:   'En cours',
  COMPLETED: 'Terminé',
  CANCELLED: 'Annulé',
};

const STATUS_COLORS: Record<string, string> = {
  RUNNING:   'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
};

@Component({
  selector: 'app-workflows-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="p-6 max-w-5xl mx-auto">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl font-bold text-gray-800">Mes Workflows</h1>
          <p class="text-sm text-gray-500 mt-0.5">Suivi des processus métier en cours et terminés</p>
        </div>
        <button (click)="load()" class="text-sm text-gray-400 hover:text-gray-600">↺ Actualiser</button>
      </div>

      <!-- Filtres statut -->
      <div class="flex gap-2 mb-4">
        @for (f of filters; track f.value) {
          <button (click)="activeFilter.set(f.value)"
                  class="px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                  [class]="activeFilter() === f.value
                    ? 'bg-indigo-600 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'">
            {{ f.label }}
          </button>
        }
      </div>

      @if (loading()) {
        <div class="text-center text-gray-400 py-16 text-sm">Chargement…</div>
      } @else if (filtered().length === 0) {
        <div class="bg-white rounded-xl border border-dashed border-gray-300 py-16 text-center">
          <p class="text-gray-400 text-sm">Aucun workflow {{ activeFilter() !== 'ALL' ? 'dans ce statut' : '' }}.</p>
        </div>
      } @else {
        <div class="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
          <table class="w-full text-sm">
            <thead>
              <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                <th class="px-4 py-3">Workflow</th>
                <th class="px-4 py-3">Étape courante</th>
                <th class="px-4 py-3">Démarré le</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              @for (wf of filtered(); track wf.id) {
                <tr class="hover:bg-gray-50 transition-colors">
                  <td class="px-4 py-3">
                    <p class="font-medium text-gray-800">{{ wf.workflowLibelle }}</p>
                    @if (wf.createdByNom) {
                      <p class="text-xs text-gray-400">par {{ wf.createdByNom }}</p>
                    }
                  </td>
                  <td class="px-4 py-3 text-gray-600">
                    {{ wf.currentStepLibelle ?? '—' }}
                  </td>
                  <td class="px-4 py-3 text-gray-500 text-xs">
                    {{ wf.startedAt | date:'dd/MM/yyyy HH:mm' }}
                  </td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium"
                          [class]="statusColor(wf.status)">
                      {{ statusLabel(wf.status) }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-right">
                    <a [routerLink]="['/workflows', wf.id]"
                       class="text-xs text-indigo-600 hover:underline font-medium">Détail →</a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="text-xs text-gray-400 mt-2 text-right">{{ filtered().length }} workflow(s)</p>
      }
    </div>
  `,
})
export class WorkflowsListComponent implements OnInit {
  private api = inject(ApiService);

  instances = signal<any[]>([]);
  loading   = signal(true);
  activeFilter = signal<StatusFilter>('ALL');

  readonly filters: { label: string; value: StatusFilter }[] = [
    { label: 'Tous',      value: 'ALL' },
    { label: 'En cours',  value: 'RUNNING' },
    { label: 'Terminés',  value: 'COMPLETED' },
    { label: 'Annulés',   value: 'CANCELLED' },
  ];

  filtered = computed(() => {
    const f = this.activeFilter();
    const all = this.instances();
    return f === 'ALL' ? all : all.filter(w => w.status === f);
  });

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getMyWorkflowInstances().subscribe({
      next: list => { this.instances.set(list); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  statusLabel(s: string) { return STATUS_LABELS[s] ?? s; }
  statusColor(s: string) { return STATUS_COLORS[s] ?? 'bg-gray-100 text-gray-500'; }
}
