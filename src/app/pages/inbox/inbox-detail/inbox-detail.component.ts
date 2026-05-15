import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-inbox-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './inbox-detail.component.html'
})
export class InboxDetailComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  courrier = signal<any>(null);
  loading = signal(true);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getCourrierArrive(id).subscribe(data => {
      this.courrier.set(data);
      this.loading.set(false);
    });
  }

  archiver() {
    const id = this.courrier()?.id;
    if (!id) return;
    this.api.updateCourrierArrive(id, { statut: 'ARCHIVE' }).subscribe(() => {
      this.router.navigate(['/inbox']);
    });
  }

  marquerEnCours() {
    const id = this.courrier()?.id;
    if (!id) return;
    this.api.updateCourrierArrive(id, { statut: 'EN_COURS' }).subscribe(updated => {
      this.courrier.set(updated);
    });
  }
}
