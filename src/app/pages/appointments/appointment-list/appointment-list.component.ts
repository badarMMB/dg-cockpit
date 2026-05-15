import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './appointment-list.component.html'
})
export class AppointmentListComponent implements OnInit {
  private api = inject(ApiService);
  rendezVous = signal<any[]>([]);
  loading = signal(true);
  today = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  statutColors: Record<string, string> = {
    'PLANIFIE':  'bg-blue-100 text-blue-700',
    'EN_COURS':  'bg-green-100 text-green-700',
    'TERMINE':   'bg-gray-100 text-gray-600',
    'ANNULE':    'bg-red-100 text-red-700'
  };

  statutLabels: Record<string, string> = {
    'PLANIFIE':  'Planifié',
    'EN_COURS':  'En cours',
    'TERMINE':   'Terminé',
    'ANNULE':    'Annulé'
  };

  ngOnInit() {
    this.api.getRendezVous().subscribe(data => {
      this.rendezVous.set(data);
      this.loading.set(false);
    });
  }
}
