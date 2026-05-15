import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-sidebar-navigation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar-navigation.component.html',
  styleUrl: './sidebar-navigation.component.css'
})
export class SidebarNavigationComponent {
  menuItems = signal([
    { label: 'Tableau de bord', icon: '📊', route: '/dashboard', unread: 0, alert: false },
    { label: 'Flux des Instructions', icon: '💬', route: '/chat', unread: 3, alert: true },
    { label: 'Éditeur de Documents', icon: '📝', route: '/editor', unread: 0, alert: false },
    { label: 'Parapheur', icon: '✍️', route: '/signature', unread: 2, alert: true },
    { label: 'Courrier Arrivé', icon: '📥', route: '/inbox', unread: 5, alert: false },
    { label: 'Courrier Départ', icon: '📤', route: '/outbox', unread: 0, alert: false },
    { label: 'Agenda & Visiteurs', icon: '📅', route: '/appointments', unread: 0, alert: false },
    { label: 'Administration', icon: '⚙️', route: '/settings', unread: 0, alert: false },
  ]);

  activeRoute = signal('/dashboard');
  isMobileMenuOpen = signal(false);

  constructor(private router: Router) {
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe((e: any) => {
      this.activeRoute.set(e.urlAfterRedirects);
      this.isMobileMenuOpen.set(false);
    });
  }

  setActive(route: string) {
    this.activeRoute.set(route);
    this.isMobileMenuOpen.set(false);
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen.update(v => !v);
  }
}
