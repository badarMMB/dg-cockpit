import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);

  stats = signal([
    { label: 'Instructions actives', value: 0, icon: '💬', color: 'text-blue-600', bg: 'bg-blue-100' },
    { label: 'Documents à signer', value: 0, icon: '✍️', color: 'text-alert', bg: 'bg-red-100' },
    { label: 'Rendez-vous du jour', value: 0, icon: '📅', color: 'text-green-600', bg: 'bg-green-100' },
  ]);

  instructionsList = signal<any[]>([]);

  parapheurDocs = signal([
    { id: 'A', title: 'Avenant_Contrat_Nettoyage.pdf', size: '2.4 MB', urgent: true },
    { id: 'B', title: 'Bilan_Financier_Avril.pdf', size: '5.1 MB', urgent: false },
    { id: 'C', title: 'Note_de_Frais_Direction.pdf', size: '1.1 MB', urgent: false },
  ]);

  ngOnInit() {
    this.api.getDashboardStats().subscribe(s => {
      this.stats.set([
        { label: 'Instructions actives', value: s.instructionsActives, icon: '💬', color: 'text-blue-600', bg: 'bg-blue-100' },
        { label: 'Documents à signer', value: s.documentsASigner, icon: '✍️', color: 'text-alert', bg: 'bg-red-100' },
        { label: 'Rendez-vous du jour', value: s.rendezVousDuJour, icon: '📅', color: 'text-green-600', bg: 'bg-green-100' },
      ]);
    });
    this.api.getInstructionsRecentes().subscribe(list => {
      this.instructionsList.set(list.map(i => ({
        id: i.id,
        title: i.title,
        agent: i.agent,
        date: i.date,
        status: i.statut
      })));
    });
  }
}
