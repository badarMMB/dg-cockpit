import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { SearchBarComponent } from '../../components/search-bar/search-bar.component';
import { filter } from 'rxjs/operators';
import { signal } from '@angular/core';
import { NotificationService } from '../../services/notification.service';
import { AuthService } from '../../services/auth.service';

const ALL_MENU_ITEMS = [
  { label: 'Tableau de bord',       icon: '📊', route: '/dashboard',    roles: ['DG', 'SECRETAIRE', 'SUBORDONNE', 'ADMIN_IT'], unread: 0, alert: false },
  { label: 'Flux des Instructions', icon: '💬', route: '/chat',              roles: ['DG', 'SECRETAIRE', 'SUBORDONNE'],             unread: 0, alert: false },
  { label: 'Notes de Service',      icon: '📋', route: '/notes-de-service', roles: ['DG', 'SECRETAIRE', 'SUBORDONNE'],             unread: 0, alert: false },
  { label: 'Bureau',                icon: '🗂️', route: '/bureau',           roles: ['SECRETAIRE'],                                 unread: 0, alert: false },
  { label: 'Parapheur',             icon: '✍️', route: '/signature',    roles: ['DG'],                                         unread: 0, alert: false },
  { label: 'Courrier Arrivé',       icon: '📥', route: '/inbox',        roles: ['DG', 'SECRETAIRE'],                           unread: 0, alert: false },
  { label: 'Courrier Départ',       icon: '📤', route: '/outbox',       roles: ['DG', 'SECRETAIRE'],                           unread: 0, alert: false },
  { label: 'Agenda & Visiteurs',    icon: '📅', route: '/appointments', roles: ['DG', 'SECRETAIRE'],                           unread: 0, alert: false },
  { label: 'Classeurs',              icon: '🗂️', route: '/classeurs',    roles: ['DG', 'SECRETAIRE'],                           unread: 0, alert: false },
  { label: 'Mes Signatures',         icon: '🖊️', route: '/signature-assets', roles: ['DG', 'SECRETAIRE', 'SUBORDONNE', 'ADMIN_IT'], unread: 0, alert: false },
  { label: 'Paramètres',            icon: '⚙️', route: '/parametres',   roles: ['DG', 'ADMIN_IT'],                             unread: 0, alert: false },
];

@Component({
  selector: 'app-sidebar-navigation',
  standalone: true,
  imports: [CommonModule, RouterModule, SearchBarComponent],
  templateUrl: './sidebar-navigation.component.html',
  styleUrl: './sidebar-navigation.component.css'
})
export class SidebarNavigationComponent {
  private auth = inject(AuthService);

  menuItems = computed(() => {
    const role = this.auth.currentUser()?.role ?? '';
    return ALL_MENU_ITEMS.filter(item => item.roles.includes(role));
  });

  currentUser = computed(() => this.auth.currentUser());

  activeRoute  = signal('/dashboard');
  isMobileMenuOpen = signal(false);
  showNotifPanel   = signal(false);

  readonly unreadCount;
  readonly notifications;

  constructor(private router: Router, notifService: NotificationService) {
    this.unreadCount  = notifService.unreadCount;
    this.notifications = notifService.notifications;

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

  toggleMobileMenu()  { this.isMobileMenuOpen.update(v => !v); }
  toggleNotifPanel()  { this.showNotifPanel.update(v => !v); }
  clearNotifs()       { this.showNotifPanel.set(false); }
  logout()            { this.auth.logout(); }

  userInitials(): string {
    const nom = this.currentUser()?.nomComplet ?? '';
    return nom.split(' ').slice(0, 2).map(w => w[0]).join('').toUpperCase() || '?';
  }

  roleLabel(): string {
    const map: Record<string, string> = {
      DG: 'Directeur Général',
      SECRETAIRE: 'Secrétaire',
      SUBORDONNE: 'Subordonné',
      ADMIN_IT: 'Admin IT',
    };
    return map[this.currentUser()?.role ?? ''] ?? '';
  }
}
