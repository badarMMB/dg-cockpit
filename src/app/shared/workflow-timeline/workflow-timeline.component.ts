import {
  Component, inject, input, signal, computed, effect
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService, WorkflowStep, WorkflowEtat } from '../../services/api.service';

// ── Types locaux ──────────────────────────────────────────────────────────

type StatutEtape = 'PASSE' | 'EN_COURS' | 'REJETE' | 'A_VENIR';

interface EtapeAffichage {
  stepOrder:         number;
  stepLabel:         string;
  posteLibelle:      string | null;
  actorInstructions: string | null;
  statut:            StatutEtape;
}

@Component({
  selector: 'app-workflow-timeline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-timeline.component.html',
})
export class WorkflowTimelineComponent {
  private api = inject(ApiService);

  // ── Inputs (signal Angular 18) ───────────────────────────────────────────
  readonly instructionId = input<string>('');
  readonly typeId        = input<string>('');

  // ── État interne ─────────────────────────────────────────────────────────
  readonly etapes     = signal<WorkflowStep[]>([]);
  readonly etat       = signal<WorkflowEtat | null>(null);
  readonly chargement = signal(false);
  readonly erreur     = signal(false);

  // ── Computed : étapes enrichies avec statut calculé ──────────────────────
  readonly etapesAffichage = computed((): EtapeAffichage[] =>
    this.etapes().map(s => ({
      stepOrder:         s.stepOrder,
      stepLabel:         s.stepLabel,
      posteLibelle:      s.requiredPoste?.libelle ?? null,
      actorInstructions: s.actorInstructions,
      statut:            this.calculerStatut(s.stepOrder, this.etat()),
    }))
  );

  /** globalStatus courant — utilisé dans le template pour le badge terminal */
  readonly globalStatut = computed(() => this.etat()?.globalStatus ?? null);

  constructor() {
    // Recharge quand l'un des deux inputs change
    effect(() => {
      const tid = this.typeId();
      const iid = this.instructionId();
      // Réinitialisation de l'état avant chaque chargement
      this.etapes.set([]);
      this.etat.set(null);
      this.erreur.set(false);
      if (tid) this.chargerEtapes(tid);
      if (iid) this.chargerEtat(iid);
    });
  }

  // ── Chargement des données ────────────────────────────────────────────────

  private chargerEtapes(typeId: string) {
    this.chargement.set(true);
    this.api.getWorkflowSteps(typeId).subscribe({
      next: steps => {
        this.etapes.set([...steps].sort((a: WorkflowStep, b: WorkflowStep) => a.stepOrder - b.stepOrder));
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set(true);
      },
    });
  }

  private chargerEtat(instructionId: string) {
    this.api.getEtatWorkflow(instructionId).subscribe({
      next: etat => this.etat.set(etat),
      error: ()   => this.etat.set(null),
    });
  }

  // ── Logique de statut ─────────────────────────────────────────────────────

  private calculerStatut(stepOrder: number, etat: WorkflowEtat | null): StatutEtape {
    if (!etat || etat.globalStatus === 'BROUILLON') return 'A_VENIR';
    if (etat.globalStatus === 'CLOTURE_VALIDE')     return 'PASSE';

    const ordreActuel = etat.currentStep?.stepOrder ?? 0;

    if (etat.globalStatus === 'EN_CIRCUIT') {
      if (stepOrder < ordreActuel)  return 'PASSE';
      if (stepOrder === ordreActuel) return 'EN_COURS';
      return 'A_VENIR';
    }

    if (etat.globalStatus === 'CLOTURE_REJETE') {
      // Les étapes antérieures ont été validées ; l'étape courante est celle du rejet
      if (stepOrder < ordreActuel)  return 'PASSE';
      if (stepOrder === ordreActuel) return 'REJETE';
      return 'A_VENIR';
    }

    return 'A_VENIR';
  }

  // ── Helpers CSS ───────────────────────────────────────────────────────────
  // Les chaînes de classes sont complètes (pas de concaténation dynamique)
  // pour que Tailwind puisse les scanner à la compilation.

  cercleCls(statut: StatutEtape): string {
    const base = 'w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 transition-colors';
    switch (statut) {
      case 'PASSE':    return `${base} bg-green-500 text-white`;
      case 'EN_COURS': return `${base} bg-indigo-600 text-white ring-2 ring-indigo-300 ring-offset-2 animate-pulse`;
      case 'REJETE':   return `${base} bg-red-500 text-white`;
      case 'A_VENIR':  return `${base} bg-gray-200 text-gray-500`;
    }
  }

  ligneCls(statut: StatutEtape): string {
    const base = 'w-0.5 flex-1 my-1 min-h-[20px]';
    switch (statut) {
      case 'PASSE':    return `${base} bg-green-400`;
      case 'EN_COURS': return `${base} bg-indigo-200`;
      case 'REJETE':   return `${base} bg-red-300`;
      case 'A_VENIR':  return `${base} bg-gray-200`;
    }
  }

  textCls(statut: StatutEtape): string {
    switch (statut) {
      case 'PASSE':    return 'text-sm font-semibold leading-tight text-green-700';
      case 'EN_COURS': return 'text-sm font-semibold leading-tight text-indigo-800';
      case 'REJETE':   return 'text-sm font-semibold leading-tight text-red-700';
      case 'A_VENIR':  return 'text-sm font-semibold leading-tight text-gray-400';
    }
  }
}
