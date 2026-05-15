import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-outbox-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './outbox-list.component.html'
})
export class OutboxListComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);

  courriers = signal<any[]>([]);
  templates = signal<any[]>([]);
  loading = signal(true);

  filterStatut = signal<string>('TOUS');
  showModal = signal(false);

  selectedTemplateId = signal<string>('');
  formObjet = signal('');
  formDestinataire = signal('');
  formContenu = signal('');
  saving = signal(false);

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

  filtered = computed(() => {
    const f = this.filterStatut();
    return f === 'TOUS' ? this.courriers() : this.courriers().filter(c => c['statut'] === f);
  });

  ngOnInit() {
    this.api.getCourriersDepart().subscribe(data => {
      this.courriers.set(data);
      this.loading.set(false);
    });
    this.api.getTemplatesCourrier().subscribe(data => this.templates.set(data));
  }

  openModal() {
    this.selectedTemplateId.set('');
    this.formObjet.set('');
    this.formDestinataire.set('');
    this.formContenu.set('');
    this.showModal.set(true);
  }

  onTemplateChange(id: string) {
    this.selectedTemplateId.set(id);
    if (!id) return;
    const tpl = this.templates().find(t => t['id'] === id);
    if (tpl) {
      this.formObjet.set(tpl['objet'] ?? '');
      this.formContenu.set(tpl['contenu'] ?? '');
    }
  }

  createDraft() {
    if (!this.formObjet() || !this.formDestinataire()) return;
    this.saving.set(true);
    this.api.createCourrierDepart({
      objet:        this.formObjet(),
      destinataire: this.formDestinataire(),
      contenu:      this.formContenu(),
      apercu:       this.formContenu().substring(0, 120)
    }).subscribe(created => {
      this.saving.set(false);
      this.showModal.set(false);
      this.router.navigate(['/outbox', created['id']]);
    });
  }
}
