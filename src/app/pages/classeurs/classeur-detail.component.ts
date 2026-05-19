import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-classeur-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './classeur-detail.component.html'
})
export class ClasseurDetailComponent implements OnInit {
  private api    = inject(ApiService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  classeur  = signal<any>(null);
  documents = signal<any[]>([]);
  loading   = signal(true);

  readonly typeLabel: Record<string, string> = {
    COURRIER_DEPART:  'Courrier Départ',
    COURRIER_ARRIVE:  'Courrier Arrivé',
    PDF_DOCUMENT:     'Document PDF',
  };

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getClasseurs().subscribe(list => {
      this.classeur.set(list.find(c => c['id'] === id) ?? null);
    });
    this.api.getClasseurDocuments(id).subscribe(docs => {
      this.documents.set(docs);
      this.loading.set(false);
    });
  }

  openScan(key: string) {
    window.open(this.api.getScanUrl(key), '_blank');
  }

  back() {
    this.router.navigate(['/classeurs']);
  }
}
