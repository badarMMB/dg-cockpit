import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuditTimelineComponent } from '../../shared/audit-timeline/audit-timeline.component';

interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  status: 'Actif' | 'Inactif';
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, AuditTimelineComponent],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);

  activeTab = signal<'company' | 'users' | 'audit'>('company');
  auditLogs = signal<any[]>([]);
  auditLoading = signal(false);

  companyName = signal('Ministère des Affaires Générales');
  companyLogo = signal('https://ui-avatars.com/api/?name=MAG&background=00236f&color=fff&size=128');
  companyHeader = signal('RÉPUBLIQUE FRANÇAISE\nMINISTÈRE DES AFFAIRES GÉNÉRALES\nDirection Générale');
  companyFooter = signal('12 Rue de la République, 75001 Paris | contact@mag.gouv.fr | +33 1 23 45 67 89');

  users = signal<User[]>([]);
  isAddUserModalOpen = signal(false);
  newUser = signal({ name: '', email: '', role: 'Agent (Chef de Service)' });
  availableRoles = ['Directeur Général (DG)', 'Secrétaire de Direction', 'Agent (Chef de Service)', 'Admin IT'];

  loadAudit() {
    this.auditLoading.set(true);
    this.api.getAuditLogs().subscribe(data => {
      this.auditLogs.set(data);
      this.auditLoading.set(false);
    });
  }

  switchTab(tab: 'company' | 'users' | 'audit') {
    this.activeTab.set(tab);
    if (tab === 'audit' && this.auditLogs().length === 0) this.loadAudit();
  }

  ngOnInit() {
    this.api.getCollaborateurs().subscribe(list => {
      this.users.set(list.map(c => ({
        id: c.id,
        name: c.name,
        email: c.email,
        role: c.role,
        status: c.status === 'ACTIF' ? 'Actif' : 'Inactif'
      })));
    });
  }

  triggerLogoUpload() {
    alert("Simulation : Ouverture de l'explorateur de fichiers pour uploader un logo.");
  }

  saveCompanySettings() {
    alert("Paramètres de la structure enregistrés avec succès.");
  }

  openAddUserModal() {
    this.newUser.set({ name: '', email: '', role: this.availableRoles[2] });
    this.isAddUserModalOpen.set(true);
  }

  updateNewUserField(field: 'name' | 'email' | 'role', value: string) {
    this.newUser.update(u => ({ ...u, [field]: value }));
  }

  addUser() {
    const user = this.newUser();
    if (!user.name || !user.email) return;
    this.api.addCollaborateur(user).subscribe(created => {
      this.users.update(list => [...list, {
        id: created.id,
        name: created.name,
        email: created.email,
        role: created.role,
        status: 'Actif'
      }]);
      this.isAddUserModalOpen.set(false);
    });
  }

  deleteUser(id: string) {
    if (confirm("Confirmer la révocation des accès pour cet utilisateur ?")) {
      this.api.deleteCollaborateur(id).subscribe(() => {
        this.users.update(list => list.filter(u => u.id !== id));
      });
    }
  }
}
