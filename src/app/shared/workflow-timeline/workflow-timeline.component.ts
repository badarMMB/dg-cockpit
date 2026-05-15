import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-workflow-timeline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-3">
      <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Historique de validation</h3>
      <div *ngFor="let step of steps" class="flex gap-3 items-start">
        <div class="flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold"
             [ngClass]="{
               'bg-yellow-100 text-yellow-700': step.statut === 'SOUMIS',
               'bg-green-100 text-green-700':  step.statut === 'VALIDE',
               'bg-red-100 text-red-700':      step.statut === 'REJETE'
             }">
          {{ step.statut === 'SOUMIS' ? '⏳' : step.statut === 'VALIDE' ? '✓' : '✗' }}
        </div>
        <div class="flex-1">
          <p class="text-xs font-semibold text-gray-800">
            {{ step.statut === 'SOUMIS' ? 'Soumis pour validation' : step.statut === 'VALIDE' ? 'Validé' : 'Rejeté' }}
            <span class="font-normal text-gray-500">par {{ step.validateur }}</span>
          </p>
          <p *ngIf="step.commentaire" class="text-xs text-gray-500 mt-0.5">{{ step.commentaire }}</p>
          <p class="text-xs text-gray-400 mt-0.5">{{ step.date | date:'dd/MM/yyyy HH:mm' }}</p>
        </div>
      </div>
      <p *ngIf="!steps?.length" class="text-xs text-gray-400 italic">Aucune étape de validation.</p>
    </div>
  `
})
export class WorkflowTimelineComponent {
  @Input() steps: any[] = [];
}
