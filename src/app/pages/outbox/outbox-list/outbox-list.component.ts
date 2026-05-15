import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-outbox-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './outbox-list.component.html'
})
export class OutboxListComponent implements OnInit {
  private api = inject(ApiService);
  courriers = signal<any[]>([]);
  loading = signal(true);

  statutColors: Record<string, string> = {
    'BROUILLON': 'bg-gray-100 text-gray-600',
    'SIGNE':     'bg-blue-100 text-blue-700',
    'EXPEDIE':   'bg-green-100 text-green-700'
  };

  statutLabels: Record<string, string> = {
    'BROUILLON': 'Brouillon',
    'SIGNE':     'Signé',
    'EXPEDIE':   'Expédié'
  };

  ngOnInit() {
    this.api.getCourriersDepart().subscribe(data => {
      this.courriers.set(data);
      this.loading.set(false);
    });
  }
}
