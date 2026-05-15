import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { AuditTimelineComponent } from '../../../shared/audit-timeline/audit-timeline.component';

@Component({
  selector: 'app-outbox-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, AuditTimelineComponent],
  templateUrl: './outbox-detail.component.html'
})
export class OutboxDetailComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  courrier = signal<any>(null);
  loading = signal(true);
  editMode = signal(false);
  saving = signal(false);

  draftObjet = signal('');
  draftDestinataire = signal('');
  draftContenu = signal('');

  readonly statutColors: Record<string, string> = {
    'BROUILLON': 'bg-gray-100 text-gray-600',
    'SIGNE':     'bg-blue-100 text-blue-700',
    'EXPEDIE':   'bg-green-100 text-green-700'
  };
  readonly statutLabels: Record<string, string> = {
    'BROUILLON': 'Brouillon',
    'SIGNE':     'Signé',
    'EXPEDIE':   'Expédié'
  };

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getCourrierDepart(id).subscribe(data => {
      this.courrier.set(data);
      this.loading.set(false);
    });
  }

  startEdit() {
    const c = this.courrier();
    this.draftObjet.set(c['objet'] ?? '');
    this.draftDestinataire.set(c['destinataire'] ?? '');
    this.draftContenu.set(c['contenu'] ?? '');
    this.editMode.set(true);
  }

  saveEdit() {
    const id = this.courrier()?.['id'];
    if (!id) return;
    this.saving.set(true);
    this.api.updateCourrierDepart(id, {
      objet:        this.draftObjet(),
      destinataire: this.draftDestinataire(),
      contenu:      this.draftContenu(),
      apercu:       this.draftContenu().substring(0, 120)
    }).subscribe(updated => {
      this.courrier.set(updated);
      this.editMode.set(false);
      this.saving.set(false);
    });
  }

  updateStatut(statut: string) {
    const id = this.courrier()?.['id'];
    if (!id) return;
    this.api.updateCourrierDepart(id, { statut }).subscribe(updated => {
      this.courrier.set(updated);
    });
  }
}
