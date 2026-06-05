import {
  Component, OnInit, signal, computed, inject
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../services/api.service';

interface WorkflowStep {
  id?: string;
  workflowId?: string;
  ordre: number;
  code: string;
  libelle: string;
  description?: string;
  stepType: string;
  autoTransition: boolean;
  nextStepId?: string | null;
  configJson?: string;
}

interface WorkflowParticipant {
  id?: string;
  workflowStepId?: string;
  posteId: string;
  posteLibelle?: string;
  roleParticipant: string;
  obligatoire: boolean;
  ordre: number;
}

const STEP_TYPE_STYLES: Record<string, string> = {
  DOCUMENT_CREATION: 'bg-blue-100 text-blue-700',
  INSTRUCTION:       'bg-amber-100 text-amber-700',
  SIGNATURE:         'bg-purple-100 text-purple-700',
  VALIDATION:        'bg-green-100 text-green-700',
  TASK:              'bg-gray-100 text-gray-700',
  NOTIFICATION:      'bg-cyan-100 text-cyan-700',
  DECISION:          'bg-orange-100 text-orange-700',
  END:               'bg-red-100 text-red-700',
};

const STEP_TYPE_LABELS: Record<string, string> = {
  DOCUMENT_CREATION: 'Création doc.',
  INSTRUCTION:       'Instruction',
  SIGNATURE:         'Signature',
  VALIDATION:        'Validation',
  TASK:              'Tâche',
  NOTIFICATION:      'Notification',
  DECISION:          'Décision',
  END:               'Fin',
};

const ROLE_LABELS: Record<string, string> = {
  RAPPORTEUR:  'Rapporteur',
  PARTICIPANT: 'Participant',
  VALIDATEUR:  'Validateur',
  OBSERVATEUR: 'Observateur',
  DECIDEUR:    'Décideur',
  SIGNATAIRE:  'Signataire',
  RESPONSABLE: 'Responsable',
};

@Component({
  selector: 'app-workflow-designer',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50">
      <!-- Header -->
      <div class="bg-white border-b border-gray-200 px-6 py-4 flex items-center gap-4">
        <a routerLink="/parametres"
           class="text-gray-400 hover:text-gray-600 text-sm flex items-center gap-1">
          ← Paramètres
        </a>
        <span class="text-gray-300">/</span>
        <span class="text-sm font-semibold text-gray-700">
          {{ workflow()?.libelle ?? 'Designer Workflow' }}
        </span>
        @if (workflow()) {
          <span [class]="'ml-2 px-2 py-0.5 rounded-full text-xs font-medium ' + (workflow().actif ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500')">
            {{ workflow().actif ? 'Actif' : 'Inactif' }}
          </span>
        }
        <div class="ml-auto flex items-center gap-2">
          <button (click)="validateWorkflow()"
                  class="px-4 py-2 rounded-lg border border-indigo-300 text-indigo-600 text-sm font-medium hover:bg-indigo-50">
            Valider
          </button>
          <button (click)="toggleActive()"
                  class="px-4 py-2 rounded-lg text-sm font-medium"
                  [class]="workflow()?.actif
                    ? 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                    : 'bg-green-600 text-white hover:bg-green-700'">
            {{ workflow()?.actif ? 'Désactiver' : 'Activer' }}
          </button>
        </div>
      </div>

      <!-- Validation result -->
      @if (validationResult()) {
        <div class="mx-6 mt-4 p-4 rounded-xl border"
             [class]="validationResult()!.valid
               ? 'bg-green-50 border-green-200'
               : 'bg-red-50 border-red-200'">
          <p class="font-medium text-sm"
             [class]="validationResult()!.valid ? 'text-green-700' : 'text-red-700'">
            {{ validationResult()!.valid ? '✓ Workflow valide' : '✗ Erreurs de validation' }}
          </p>
          @if (!validationResult()!.valid) {
            <ul class="mt-2 space-y-1">
              @for (err of validationResult()!.errors; track err) {
                <li class="text-sm text-red-600">• {{ err }}</li>
              }
            </ul>
          }
        </div>
      }

      <div class="p-6 flex gap-6">
        <!-- Colonne étapes -->
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between mb-4">
            <h2 class="font-semibold text-gray-700">Étapes du workflow</h2>
            <button (click)="openStepModal()"
                    class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
              + Ajouter étape
            </button>
          </div>

          @if (loadingSteps()) {
            <p class="text-gray-400 text-sm">Chargement…</p>
          } @else if (steps().length === 0) {
            <div class="bg-white rounded-xl border border-dashed border-gray-300 py-16 text-center">
              <p class="text-gray-400 text-sm">Aucune étape. Cliquez sur "+ Ajouter étape".</p>
            </div>
          } @else {
            <div class="space-y-3">
              @for (step of steps(); track step.id; let i = $index) {
                <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-4"
                     [class.ring-2]="selectedStepId() === step.id"
                     [class.ring-indigo-400]="selectedStepId() === step.id">
                  <div class="flex items-start gap-3">
                    <!-- Ordre + move buttons -->
                    <div class="flex flex-col items-center gap-1 pt-0.5">
                      <span class="text-xs font-mono text-gray-400 w-5 text-center">{{ step.ordre }}</span>
                      <button (click)="moveStep(i, -1)" [disabled]="i === 0"
                              class="text-gray-300 hover:text-gray-500 disabled:opacity-20 text-xs leading-none">▲</button>
                      <button (click)="moveStep(i, 1)" [disabled]="i === steps().length - 1"
                              class="text-gray-300 hover:text-gray-500 disabled:opacity-20 text-xs leading-none">▼</button>
                    </div>

                    <!-- Contenu -->
                    <div class="flex-1 min-w-0">
                      <div class="flex items-center gap-2 flex-wrap">
                        <span class="font-medium text-sm text-gray-800">{{ step.libelle }}</span>
                        <span class="font-mono text-xs text-gray-400 uppercase">{{ step.code }}</span>
                        <span [class]="'px-2 py-0.5 rounded-full text-xs font-medium ' + stepTypeStyle(step.stepType)">
                          {{ stepTypeLabel(step.stepType) }}
                        </span>
                        @if (step.autoTransition) {
                          <span class="px-2 py-0.5 rounded-full text-xs bg-indigo-50 text-indigo-500">auto</span>
                        }
                      </div>
                      @if (step.description) {
                        <p class="text-xs text-gray-500 mt-1">{{ step.description }}</p>
                      }
                      @if (step.nextStepId) {
                        <p class="text-xs text-gray-400 mt-1">
                          → {{ stepLibelle(step.nextStepId) }}
                        </p>
                      }
                      <!-- Participants count -->
                      @if (participantsByStep()[step.id!]?.length) {
                        <p class="text-xs text-gray-400 mt-1">
                          {{ participantsByStep()[step.id!].length }} participant(s)
                        </p>
                      }
                    </div>

                    <!-- Actions -->
                    <div class="flex items-center gap-2 ml-2 shrink-0">
                      <button (click)="openParticipants(step)"
                              class="text-xs text-indigo-600 hover:underline">Participants</button>
                      <button (click)="openStepModal(step)"
                              class="text-xs text-blue-600 hover:underline">Modifier</button>
                      <button (click)="deleteStep(step)"
                              class="text-xs text-red-500 hover:underline">Supprimer</button>
                    </div>
                  </div>
                </div>

                <!-- Flèche entre étapes -->
                @if (i < steps().length - 1) {
                  <div class="flex justify-center text-gray-300 text-lg leading-none">↓</div>
                }
              }
            </div>
          }
        </div>

        <!-- Panneau participants (side panel) -->
        @if (showParticipantsPanel()) {
          <div class="w-80 shrink-0">
            <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
              <div class="flex items-center justify-between mb-3">
                <h3 class="font-semibold text-sm text-gray-700">
                  Participants — {{ selectedStep()?.libelle }}
                </h3>
                <button (click)="showParticipantsPanel.set(false)"
                        class="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
              </div>

              <div class="space-y-2">
                @for (p of editingParticipants(); track $index; let pi = $index) {
                  <div class="border border-gray-100 rounded-lg p-2 space-y-1.5">
                    <select [(ngModel)]="p.posteId"
                            class="w-full border border-gray-200 rounded px-2 py-1 text-xs">
                      <option value="">-- Poste --</option>
                      @for (poste of postes(); track poste.id) {
                        <option [value]="poste.id">{{ poste.libelle }}</option>
                      }
                    </select>
                    <select [(ngModel)]="p.roleParticipant"
                            class="w-full border border-gray-200 rounded px-2 py-1 text-xs">
                      @for (role of roles; track role) {
                        <option [value]="role">{{ roleLabel(role) }}</option>
                      }
                    </select>
                    <div class="flex items-center gap-2">
                      <label class="flex items-center gap-1 text-xs text-gray-600 cursor-pointer">
                        <input type="checkbox" [(ngModel)]="p.obligatoire" />
                        Obligatoire
                      </label>
                      <button (click)="removeParticipant(pi)"
                              class="ml-auto text-red-400 hover:text-red-600 text-xs">Retirer</button>
                    </div>
                  </div>
                }
              </div>

              <button (click)="addParticipant()"
                      class="mt-3 w-full border border-dashed border-gray-300 rounded-lg py-1.5 text-xs text-gray-500 hover:text-gray-700 hover:border-gray-400">
                + Ajouter participant
              </button>

              <button (click)="saveParticipants()"
                      [disabled]="savingParticipants()"
                      class="mt-3 w-full bg-indigo-600 text-white rounded-lg py-2 text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
                {{ savingParticipants() ? 'Enregistrement…' : 'Enregistrer' }}
              </button>
            </div>
          </div>
        }
      </div>
    </div>

    <!-- Modal Étape -->
    @if (showStepModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" (click.self)="showStepModal.set(false)">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingStep ? 'Modifier l\'étape' : 'Nouvelle étape' }}
          </h2>
          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Code *</label>
                <input [(ngModel)]="stepForm['code']" placeholder="Ex: SIGN_DG"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono uppercase" />
              </div>
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Ordre</label>
                <input type="number" [(ngModel)]="stepForm['ordre']"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Libellé *</label>
              <input [(ngModel)]="stepForm['libelle']" placeholder="Ex: Signature du DG"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Type d'étape *</label>
              <select [(ngModel)]="stepForm['stepType']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                @for (st of stepTypes; track st) {
                  <option [value]="st">{{ stepTypeLabel(st) }}</option>
                }
              </select>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Description</label>
              <textarea [(ngModel)]="stepForm['description']" rows="2"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none"></textarea>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Étape suivante (nextStepId)</label>
              <select [(ngModel)]="stepForm['nextStepId']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                <option value="">-- Aucune --</option>
                @for (s of steps(); track s.id) {
                  @if (s.id !== editingStep?.id) {
                    <option [value]="s.id">{{ s.libelle }} ({{ s.code }})</option>
                  }
                }
              </select>
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" id="autoTransition" [(ngModel)]="stepForm['autoTransition']" />
              <label for="autoTransition" class="text-sm text-gray-700 cursor-pointer">Transition automatique</label>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Config JSON (optionnel)</label>
              <textarea [(ngModel)]="stepForm['configJson']" rows="3"
                        placeholder='{"circuitJson": "..."}'
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-xs font-mono resize-none"></textarea>
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showStepModal.set(false)"
                    class="px-4 py-2 rounded-lg border border-gray-300 text-sm text-gray-600 hover:bg-gray-50">
              Annuler
            </button>
            <button (click)="saveStep()" [disabled]="savingStep()"
                    class="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
              {{ savingStep() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class WorkflowDesignerComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private api    = inject(ApiService);

  workflowId = '';

  workflow         = signal<any>(null);
  steps            = signal<WorkflowStep[]>([]);
  loadingSteps     = signal(false);
  postes           = signal<any[]>([]);
  participantsByStep = signal<Record<string, WorkflowParticipant[]>>({});

  showStepModal   = signal(false);
  savingStep      = signal(false);
  editingStep: WorkflowStep | null = null;
  stepForm: Record<string, any> = {};

  showParticipantsPanel = signal(false);
  selectedStepId        = signal<string | null>(null);
  selectedStep          = computed(() => this.steps().find(s => s.id === this.selectedStepId()) ?? null);
  editingParticipants   = signal<WorkflowParticipant[]>([]);
  savingParticipants    = signal(false);

  validationResult = signal<{ valid: boolean; errors: string[] } | null>(null);

  readonly stepTypes = Object.keys(STEP_TYPE_LABELS);
  readonly roles     = Object.keys(ROLE_LABELS);

  ngOnInit() {
    this.workflowId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadAll();
  }

  private loadAll() {
    this.api.getWorkflows().subscribe(wfs => {
      this.workflow.set(wfs.find(w => w.id === this.workflowId) ?? null);
    });
    this.loadSteps();
    this.api.getPostes().subscribe(p => this.postes.set(p));
  }

  private loadSteps() {
    this.loadingSteps.set(true);
    this.api.getWorkflowSteps(this.workflowId).subscribe({
      next: steps => {
        const sorted = [...steps].sort((a, b) => a.ordre - b.ordre);
        this.steps.set(sorted);
        this.loadingSteps.set(false);
        sorted.forEach(s => this.loadParticipantsFor(s.id!));
      },
      error: () => this.loadingSteps.set(false),
    });
  }

  private loadParticipantsFor(stepId: string) {
    this.api.getWorkflowStepParticipants(this.workflowId, stepId).subscribe(list => {
      this.participantsByStep.update(m => ({ ...m, [stepId]: list }));
    });
  }

  // ── Step modal ──────────────────────────────────────────────────────────────

  openStepModal(step?: WorkflowStep) {
    this.editingStep = step ?? null;
    const nextOrdre = this.steps().length;
    this.stepForm = {
      code:           step?.code           ?? '',
      libelle:        step?.libelle        ?? '',
      description:    step?.description   ?? '',
      stepType:       step?.stepType      ?? 'TASK',
      ordre:          step?.ordre         ?? nextOrdre,
      autoTransition: step?.autoTransition ?? false,
      nextStepId:     step?.nextStepId    ?? '',
      configJson:     step?.configJson    ?? '',
    };
    this.showStepModal.set(true);
  }

  saveStep() {
    if (!this.stepForm['code'] || !this.stepForm['libelle']) return;
    this.savingStep.set(true);
    const body = { ...this.stepForm, nextStepId: this.stepForm['nextStepId'] || null };
    const obs = this.editingStep
      ? this.api.updateWorkflowStep(this.workflowId, this.editingStep.id!, body)
      : this.api.createWorkflowStep(this.workflowId, body);

    obs.subscribe({
      next: () => { this.savingStep.set(false); this.showStepModal.set(false); this.loadSteps(); },
      error: () => this.savingStep.set(false),
    });
  }

  deleteStep(step: WorkflowStep) {
    if (!confirm(`Supprimer l'étape "${step.libelle}" ?`)) return;
    this.api.deleteWorkflowStep(this.workflowId, step.id!).subscribe(() => this.loadSteps());
  }

  moveStep(index: number, dir: -1 | 1) {
    const arr = [...this.steps()];
    const target = index + dir;
    if (target < 0 || target >= arr.length) return;
    [arr[index], arr[target]] = [arr[target], arr[index]];
    arr.forEach((s, i) => s.ordre = i);
    this.steps.set(arr);
    this.api.reorderWorkflowSteps(this.workflowId, arr.map(s => s.id!)).subscribe();
  }

  // ── Participants panel ──────────────────────────────────────────────────────

  openParticipants(step: WorkflowStep) {
    this.selectedStepId.set(step.id!);
    const existing = this.participantsByStep()[step.id!] ?? [];
    this.editingParticipants.set(existing.map(p => ({ ...p })));
    this.showParticipantsPanel.set(true);
  }

  addParticipant() {
    this.editingParticipants.update(list => [
      ...list,
      { posteId: '', roleParticipant: 'PARTICIPANT', obligatoire: false, ordre: list.length }
    ]);
  }

  removeParticipant(i: number) {
    this.editingParticipants.update(list => list.filter((_, idx) => idx !== i));
  }

  saveParticipants() {
    const stepId = this.selectedStepId();
    if (!stepId) return;
    this.savingParticipants.set(true);
    const valid = this.editingParticipants().filter(p => p.posteId);
    this.api.saveWorkflowStepParticipants(this.workflowId, stepId, valid).subscribe({
      next: saved => {
        this.savingParticipants.set(false);
        this.participantsByStep.update(m => ({ ...m, [stepId]: saved }));
      },
      error: () => this.savingParticipants.set(false),
    });
  }

  // ── Validation / Toggle ─────────────────────────────────────────────────────

  validateWorkflow() {
    this.api.validateWorkflow(this.workflowId).subscribe(result => {
      this.validationResult.set(result);
    });
  }

  toggleActive() {
    this.api.toggleWorkflow(this.workflowId).subscribe(saved => {
      this.workflow.set(saved);
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  stepTypeStyle(type: string)  { return STEP_TYPE_STYLES[type] ?? 'bg-gray-100 text-gray-700'; }
  stepTypeLabel(type: string)  { return STEP_TYPE_LABELS[type] ?? type; }
  roleLabel(role: string)      { return ROLE_LABELS[role] ?? role; }

  stepLibelle(stepId: string): string {
    return this.steps().find(s => s.id === stepId)?.libelle ?? stepId;
  }
}
