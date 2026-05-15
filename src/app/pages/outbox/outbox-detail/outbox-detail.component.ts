import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-outbox-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './outbox-detail.component.html'
})
export class OutboxDetailComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  courrier = signal<any>(null);
  loading = signal(true);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getCourrierDepart(id).subscribe(data => {
      this.courrier.set(data);
      this.loading.set(false);
    });
  }

  marquerExpedie() {
    const id = this.courrier()?.id;
    if (!id) return;
    this.api.updateCourrierDepart(id, { statut: 'EXPEDIE' }).subscribe(updated => {
      this.courrier.set(updated);
    });
  }
}
