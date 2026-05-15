import { Component, OnInit, signal, computed, inject } from '@angular/core';
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
    { label: 'Documents à signer',   value: 0, icon: '✍️', color: 'text-alert',    bg: 'bg-red-100'  },
    { label: 'Rendez-vous du jour',  value: 0, icon: '📅', color: 'text-green-600', bg: 'bg-green-100' },
  ]);

  rawStats = signal<any>({});
  instructionsList = signal<any[]>([]);

  readonly statutColors: Record<string, string> = {
    'OUVERT':            'bg-gray-100 text-gray-700',
    'EN_COURS':          'bg-yellow-100 text-yellow-700',
    'EN_ATTENTE':        'bg-orange-100 text-orange-700',
    'SOUMIS_VALIDATION': 'bg-blue-100 text-blue-700',
    'CLOTURE':           'bg-green-100 text-green-700',
    'REFUSE':            'bg-red-100 text-red-700'
  };

  readonly statutLabels: Record<string, string> = {
    'OUVERT':            'Ouvert',
    'EN_COURS':          'En cours',
    'EN_ATTENTE':        'En attente',
    'SOUMIS_VALIDATION': 'En validation',
    'CLOTURE':           'Clôturé',
    'REFUSE':            'Refusé'
  };

  parapheurDocs = signal([
    { id: 'A', title: 'Avenant_Contrat_Nettoyage.pdf', size: '2.4 MB', urgent: true  },
    { id: 'B', title: 'Bilan_Financier_Avril.pdf',     size: '5.1 MB', urgent: false },
    { id: 'C', title: 'Note_de_Frais_Direction.pdf',   size: '1.1 MB', urgent: false },
  ]);

  instrChartRows = computed(() => {
    const s = this.rawStats();
    const by = s['instructionsByStatut'] ?? {};
    const total = (s['totalInstructions'] as number) || 1;
    return [
      { label: 'Ouvert',        key: 'OUVERT',            color: 'bg-gray-400'  },
      { label: 'En cours',      key: 'EN_COURS',          color: 'bg-yellow-400'},
      { label: 'En attente',    key: 'EN_ATTENTE',        color: 'bg-orange-400'},
      { label: 'En validation', key: 'SOUMIS_VALIDATION', color: 'bg-blue-500'  },
      { label: 'Clôturé',       key: 'CLOTURE',           color: 'bg-green-500' },
      { label: 'Refusé',        key: 'REFUSE',            color: 'bg-red-500'   },
    ].map(r => ({ ...r, count: by[r.key] ?? 0, pct: Math.round(((by[r.key] ?? 0) / total) * 100) }));
  });

  caChartRows = computed(() => {
    const s = this.rawStats();
    const by = s['courrierArriveByStatut'] ?? {};
    const total = (s['totalCourrierArrive'] as number) || 1;
    return [
      { label: 'Non traité', key: 'NON_TRAITE', color: 'bg-red-400'    },
      { label: 'En cours',   key: 'EN_COURS',   color: 'bg-yellow-400' },
      { label: 'Archivé',    key: 'ARCHIVE',    color: 'bg-gray-300'   },
    ].map(r => ({ ...r, count: by[r.key] ?? 0, pct: Math.round(((by[r.key] ?? 0) / total) * 100) }));
  });

  cdChartRows = computed(() => {
    const s = this.rawStats();
    const by = s['courrierDepartByStatut'] ?? {};
    const total = (s['totalCourrierDepart'] as number) || 1;
    return [
      { label: 'Brouillon', key: 'BROUILLON', color: 'bg-gray-400'  },
      { label: 'Signé',     key: 'SIGNE',     color: 'bg-blue-400'  },
      { label: 'Expédié',   key: 'EXPEDIE',   color: 'bg-green-500' },
    ].map(r => ({ ...r, count: by[r.key] ?? 0, pct: Math.round(((by[r.key] ?? 0) / total) * 100) }));
  });

  ngOnInit() {
    this.api.getDashboardStats().subscribe(s => {
      this.rawStats.set(s);
      this.stats.set([
        { label: 'Instructions actives', value: s['instructionsActives'], icon: '💬', color: 'text-blue-600',  bg: 'bg-blue-100'  },
        { label: 'Documents à signer',   value: s['documentsASigner'],    icon: '✍️', color: 'text-alert',     bg: 'bg-red-100'   },
        { label: 'Rendez-vous du jour',  value: s['rendezVousDuJour'],    icon: '📅', color: 'text-green-600', bg: 'bg-green-100' },
      ]);
    });
    this.api.getInstructionsRecentes().subscribe(list => {
      this.instructionsList.set(list.map((i: any) => ({
        id: i['id'], title: i['title'], agent: i['agent'], date: i['date'], status: i['statut']
      })));
    });
  }
}
