import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { SearchBarComponent } from '../../components/search-bar/search-bar.component';
import { filter } from 'rxjs/operators';
import { signal } from '@angular/core';
import { NotificationService } from '../../services/notification.service';
import { AuthService, AppUser } from '../../services/auth.service';

type MenuItem = {
  label: string; icon: string; route: string; unread: number; alert: boolean;
  permissions?: string[];
  visible?: (u: AppUser | null) => boolean;
};

const ALL_MENU_ITEMS: MenuItem[] = [
  { label: 'Tableau de bord',       icon: '📊', route: '/dashboard',        visible: u => u !== null, unread: 0, alert: false },
  { label: 'Flux des Instructions', icon: '💬', route: '/chat',             visible: u => u !== null, unread: 0, alert: false },
  { label: 'Notes de Service',      icon: '📋', route: '/notes-de-service', visible: u => u !== null, unread: 0, alert: false },
  { label: 'Mon Bureau',            icon: '🗂️', route: '/bureau',           visible: u => u !== null,                              unread: 0, alert: false },
  { label: 'Parapheur',             icon: '✍️', route: '/signature',        visible: u => u !== null,                              unread: 0, alert: false },
  { label: 'Courrier Arrivé',       icon: '📥', route: '/inbox',            permissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'],           unread: 0, alert: false },
  { label: 'Courrier Départ',       icon: '📤', route: '/outbox',           permissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'],           unread: 0, alert: false },
  { label: 'Agenda & Visiteurs',    icon: '📅', route: '/appointments',     permissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'],           unread: 0, alert: false },
  { label: 'Classeurs',             icon: '🗂️', route: '/classeurs',        permissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'],           unread: 0, alert: false },
  { label: 'Mes Signatures',        icon: '🖊️', route: '/signature-assets', visible: u => u !== null,                              unread: 0, alert: false },
  { label: 'Mes Workflows',         icon: '🔄', route: '/workflows',        visible: u => u !== null,                              unread: 0, alert: false },
  { label: 'Paramètres',            icon: '⚙️', route: '/parametres',       permissions: ['CAN_MANAGE_USERS', 'CAN_MANAGE_TYPES'], unread: 0, alert: false },
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
    const user = this.auth.currentUser();
    return ALL_MENU_ITEMS.filter(item =>
      item.visible ? item.visible(user) : this.hasAnyPermission(item.permissions ?? [])
    );
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

  private hasAnyPermission(permissions: string[]): boolean {
    return permissions.length === 0 || permissions.some(p => this.auth.hasPermission(p));
  }

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
