import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-inbox-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './inbox-list.component.html'
})
export class InboxListComponent implements OnInit {
  private api = inject(ApiService);
  courriers = signal<any[]>([]);
  loading = signal(true);

  statutColors: Record<string, string> = {
    'NON_TRAITE': 'bg-red-100 text-red-700',
    'EN_COURS':   'bg-yellow-100 text-yellow-700',
    'ARCHIVE':    'bg-gray-100 text-gray-600'
  };

  statutLabels: Record<string, string> = {
    'NON_TRAITE': 'Non traité',
    'EN_COURS':   'En cours',
    'ARCHIVE':    'Archivé'
  };

  ngOnInit() {
    this.api.getCourriersArrive().subscribe(data => {
      this.courriers.set(data);
      this.loading.set(false);
    });
  }
}
