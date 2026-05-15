import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, switchMap } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-search-bar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './search-bar.component.html'
})
export class SearchBarComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private router = inject(Router);

  query = signal('');
  results = signal<any[]>([]);
  isOpen = signal(false);
  isLoading = signal(false);

  private query$ = new Subject<string>();
  private sub?: Subscription;

  private readonly typeIcons: Record<string, string> = {
    'instruction':     '💬',
    'courrier-arrive': '📥',
    'courrier-depart': '📤',
    'rendez-vous':     '📅',
    'collaborateur':   '👤'
  };

  private readonly typeLabels: Record<string, string> = {
    'instruction':     'Instruction',
    'courrier-arrive': 'Courrier Arrivé',
    'courrier-depart': 'Courrier Départ',
    'rendez-vous':     'Rendez-vous',
    'collaborateur':   'Collaborateur'
  };

  ngOnInit() {
    this.sub = this.query$.pipe(
      debounceTime(300),
      filter(q => q.trim().length >= 2),
      distinctUntilChanged(),
      switchMap(q => {
        this.isLoading.set(true);
        return this.api.search(q);
      })
    ).subscribe({
      next: results => {
        this.results.set(results);
        this.isOpen.set(true);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  onInput(value: string) {
    this.query.set(value);
    if (value.trim().length < 2) {
      this.results.set([]);
      this.isOpen.set(false);
      return;
    }
    this.query$.next(value);
  }

  navigate(result: any) {
    this.router.navigateByUrl(result['route']);
    this.close();
  }

  close() {
    this.isOpen.set(false);
    this.query.set('');
    this.results.set([]);
  }

  icon(type: string): string {
    return this.typeIcons[type] ?? '📄';
  }

  label(type: string): string {
    return this.typeLabels[type] ?? type;
  }
}
