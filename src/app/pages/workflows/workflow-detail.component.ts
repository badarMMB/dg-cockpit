import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

const ACTION_ICONS: Record<string, string> = {
  ENTER_STEP:        '▶',
  CREATE_INSTRUCTION:'📋',
  CREATE_SIGNATURE:  '✍',
  SIGNER:            '✅',
  VALIDER:           '✅',
  REJETER:           '✗',
  RENVOYER:          '↩',
  CLOTURER:          '🔒',
  COMPLETE:          '🏁',
  CANCEL:            '✕',
};

const STATUS_CONFIG: Record<string, { label: string; color: string; dot: string }> = {
  RUNNING:   { label: 'En cours',  color: 'text-blue-700 bg-blue-100',   dot: 'bg-blue-500' },
  COMPLETED: { label: 'Terminé',   color: 'text-green-700 bg-green-100', dot: 'bg-green-500' },
  CANCELLED: { label: 'Annulé',    color: 'text-gray-600 bg-gray-100',   dot: 'bg-gray-400' },
};

@Component({
  selector: 'app-workflow-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="p-6 max-w-3xl mx-auto">

      <!-- Breadcrumb -->
      <div class="flex items-center gap-2 text-sm text-gray-500 mb-6">
        <a routerLink="/workflows" class="hover:text-gray-700">Mes Workflows</a>
        <span>/</span>
        <span class="text-gray-700 font-medium">{{ instance()?.workflowLibelle ?? '…' }}</span>
      </div>

      @if (loading()) {
        <div class="text-center text-gray-400 py-16">Chargement…</div>
      } @else if (instance()) {
        <!-- Header -->
        <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-5 mb-6">
          <div class="flex items-start justify-between gap-4">
            <div>
              <h1 class="text-lg font-bold text-gray-800">{{ instance()!.workflowLibelle }}</h1>
              @if (instance()!.createdByNom) {
                <p class="text-sm text-gray-500 mt-0.5">Initié par {{ instance()!.createdByNom }}</p>
              }
            </div>
            <span class="px-3 py-1 rounded-full text-xs font-medium shrink-0"
                  [class]="statusConfig(instance()!.status).color">
              {{ statusConfig(instance()!.status).label }}
            </span>
          </div>

          <div class="grid grid-cols-2 gap-4 mt-4 text-sm">
            <div>
              <p class="text-xs text-gray-400 uppercase tracking-wide">Étape courante</p>
              <p class="font-medium text-gray-700 mt-0.5">{{ instance()!.currentStepLibelle ?? '—' }}</p>
            </div>
            <div>
              <p class="text-xs text-gray-400 uppercase tracking-wide">Démarré le</p>
              <p class="font-medium text-gray-700 mt-0.5">{{ instance()!.startedAt | date:'dd/MM/yyyy à HH:mm' }}</p>
            </div>
            @if (instance()!.completedAt) {
              <div>
                <p class="text-xs text-gray-400 uppercase tracking-wide">Terminé le</p>
                <p class="font-medium text-gray-700 mt-0.5">{{ instance()!.completedAt | date:'dd/MM/yyyy à HH:mm' }}</p>
              </div>
            }
          </div>

          <!-- Actions -->
          @if (instance()!.status === 'RUNNING') {
            <div class="flex gap-2 mt-5 pt-4 border-t border-gray-100">
              <button (click)="nextStep()" [disabled]="acting()"
                      class="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
                {{ acting() ? '…' : '▶ Étape suivante' }}
              </button>
              <button (click)="showCancelModal.set(true)"
                      class="px-4 py-2 rounded-lg border border-red-300 text-red-600 text-sm hover:bg-red-50">
                Annuler le workflow
              </button>
            </div>
          }
        </div>

        <!-- Timeline des actions -->
        <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <h2 class="font-semibold text-gray-700 mb-4">Historique des actions</h2>
          @if (actions().length === 0) {
            <p class="text-gray-400 text-sm text-center py-6">Aucune action enregistrée.</p>
          } @else {
            <div class="relative">
              <!-- Ligne verticale -->
              <div class="absolute left-4 top-2 bottom-2 w-px bg-gray-200"></div>
              <div class="space-y-4">
                @for (action of actions(); track action.id) {
                  <div class="flex gap-4 items-start">
                    <!-- Dot -->
                    <div class="w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10 text-sm"
                         [class]="action.action === 'COMPLETE' ? 'bg-green-100 text-green-700' :
                                  action.action === 'CANCEL'   ? 'bg-red-100 text-red-600' :
                                  'bg-indigo-50 text-indigo-600'">
                      {{ actionIcon(action.action) }}
                    </div>
                    <!-- Contenu -->
                    <div class="flex-1 pb-4">
                      <div class="flex items-baseline gap-2">
                        <span class="text-sm font-medium text-gray-800">{{ action.stepLibelle ?? action.action }}</span>
                        <span class="text-xs text-gray-400">{{ action.createdAt | date:'dd/MM HH:mm' }}</span>
                      </div>
                      @if (action.userNom) {
                        <p class="text-xs text-gray-500 mt-0.5">par {{ action.userNom }}</p>
                      }
                      @if (action.commentaire) {
                        <p class="text-xs text-gray-600 italic mt-1 bg-gray-50 rounded px-2 py-1">
                          {{ action.commentaire }}
                        </p>
                      }
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="text-center text-gray-400 py-16">Workflow introuvable.</div>
      }
    </div>

    <!-- Modal annulation -->
    @if (showCancelModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
           (click.self)="showCancelModal.set(false)">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm">
          <h2 class="text-lg font-semibold mb-3">Annuler ce workflow ?</h2>
          <p class="text-sm text-gray-500 mb-3">Cette action est irréversible.</p>
          <textarea [(ngModel)]="cancelMotif" rows="3" placeholder="Motif (optionnel)"
                    class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none mb-4">
          </textarea>
          <div class="flex justify-end gap-3">
            <button (click)="showCancelModal.set(false)"
                    class="px-4 py-2 rounded-lg border border-gray-300 text-sm text-gray-600 hover:bg-gray-50">
              Fermer
            </button>
            <button (click)="cancelWorkflow()" [disabled]="acting()"
                    class="px-4 py-2 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50">
              {{ acting() ? '…' : 'Confirmer l\'annulation' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class WorkflowDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api   = inject(ApiService);

  private instanceId = '';

  instance = signal<any>(null);
  actions  = signal<any[]>([]);
  loading  = signal(true);
  acting   = signal(false);

  showCancelModal = signal(false);
  cancelMotif     = '';

  ngOnInit() {
    this.instanceId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getWorkflowInstance(this.instanceId).subscribe({
      next: inst => { this.instance.set(inst); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
    this.api.getWorkflowInstanceActions(this.instanceId).subscribe(
      list => this.actions.set(list)
    );
  }

  nextStep() {
    this.acting.set(true);
    this.api.workflowNextStep(this.instanceId).subscribe({
      next: inst => { this.instance.set(inst); this.acting.set(false); this.load(); },
      error: ()   => this.acting.set(false),
    });
  }

  cancelWorkflow() {
    this.acting.set(true);
    this.api.cancelWorkflowInstance(this.instanceId, this.cancelMotif || undefined).subscribe({
      next: () => {
        this.acting.set(false);
        this.showCancelModal.set(false);
        this.cancelMotif = '';
        this.load();
      },
      error: () => this.acting.set(false),
    });
  }

  actionIcon(action: string) { return ACTION_ICONS[action] ?? '•'; }
  statusConfig(s: string)    { return STATUS_CONFIG[s] ?? STATUS_CONFIG['RUNNING']; }
}
