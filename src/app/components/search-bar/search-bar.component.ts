import { Component, signal, inject, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

@Component({
  selector: 'app-search-bar',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './search-bar.component.html'
})
export class SearchBarComponent {
  private api = inject(ApiService);
  private eRef = inject(ElementRef);

  query = signal('');
  results = signal<any[]>([]);
  showResults = signal(false);
  loading = signal(false);

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(q => {
      if (q.length < 2) {
        this.results.set([]);
        this.loading.set(false);
        return;
      }
      this.performSearch(q);
    });
  }

  onInput(event: Event) {
    const q = (event.target as HTMLInputElement).value;
    this.query.set(q);
    this.loading.set(true);
    this.showResults.set(true);
    this.searchSubject.next(q);
  }

  performSearch(q: string) {
    // Simulation de recherche globale
    // Dans un vrai projet, on appellerait this.api.search(q)
    const mockResults = [
      { id: '1', type: 'instruction', titre: 'Achat de nouveaux serveurs', icon: '📝' },
      { id: '2', type: 'courrier', titre: 'Réponse Ministère Finance', icon: '📩' },
      { id: '3', type: 'rdv', titre: 'Réunion avec le DSI', icon: '📅' },
      { id: '4', type: 'collaborateur', titre: 'Jean Dupont (DSI)', icon: '👤' }
    ].filter(r => r.titre.toLowerCase().includes(q.toLowerCase()));

    setTimeout(() => {
      this.results.set(mockResults);
      this.loading.set(false);
    }, 400);
  }

  @HostListener('document:click', ['$event'])
  clickout(event: any) {
    if (!this.eRef.nativeElement.contains(event.target)) {
      this.showResults.set(false);
    }
  }

  selectResult() {
    this.showResults.set(false);
    this.query.set('');
  }
}
