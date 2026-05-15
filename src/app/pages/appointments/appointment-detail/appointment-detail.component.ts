import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './appointment-detail.component.html'
})
export class AppointmentDetailComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  rdv = signal<any>(null);
  loading = signal(true);
  showBadge = signal(false);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getRendezVousById(id).subscribe(data => {
      this.rdv.set(data);
      this.loading.set(false);
    });
  }

  toggleBadge() {
    this.showBadge.update(v => !v);
  }

  annuler() {
    const id = this.rdv()?.id;
    if (!id || !confirm('Confirmer l\'annulation de ce rendez-vous ?')) return;
    this.api.updateRendezVous(id, { statut: 'ANNULE' }).subscribe(() => {
      this.router.navigate(['/appointments']);
    });
  }
}
