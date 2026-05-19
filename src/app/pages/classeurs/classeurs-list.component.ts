import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-classeurs-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './classeurs-list.component.html'
})
export class ClasseursListComponent implements OnInit {
  private api = inject(ApiService);

  classeurs = signal<any[]>([]);
  loading = signal(true);
  showModal = signal(false);
  saving = signal(false);

  formNom = signal('');
  formDescription = signal('');
  formCouleur = signal('#3B82F6');
  formType = signal('LIBRE');

  readonly typeOptions = [
    { value: 'LIBRE',        label: 'Libre' },
    { value: 'MENSUEL',      label: 'Mensuel' },
    { value: 'ANNUEL',       label: 'Annuel' },
    { value: 'DESTINATAIRE', label: 'Par destinataire' },
    { value: 'NATURE',       label: 'Par nature' },
  ];

  readonly couleurOptions = [
    '#3B82F6', '#10B981', '#F59E0B', '#EF4444',
    '#8B5CF6', '#EC4899', '#06B6D4', '#6B7280'
  ];

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.getClasseurs().subscribe(data => {
      this.classeurs.set(data);
      this.loading.set(false);
    });
  }

  openModal() {
    this.formNom.set('');
    this.formDescription.set('');
    this.formCouleur.set('#3B82F6');
    this.formType.set('LIBRE');
    this.showModal.set(true);
  }

  create() {
    if (!this.formNom()) return;
    this.saving.set(true);
    this.api.createClasseur({
      nom: this.formNom(),
      description: this.formDescription(),
      couleur: this.formCouleur(),
      type: this.formType()
    }).subscribe(() => {
      this.saving.set(false);
      this.showModal.set(false);
      this.load();
    });
  }

  deleteClasseur(id: string, systeme: boolean, event: Event) {
    event.preventDefault();
    event.stopPropagation();
    if (systeme) return;
    if (!confirm('Supprimer ce classeur ?')) return;
    this.api.deleteClasseur(id).subscribe(() => this.load());
  }
}
