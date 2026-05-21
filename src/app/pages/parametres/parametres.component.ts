import { Component, signal, inject, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { SignatureAssetsComponent } from '../signature-assets/signature-assets.component';

type Tab = 'INSTRUCTION_TYPES' | 'PROOF_TYPES' | 'USERS' | 'SIGNATURE_ASSETS';

type Categorie = 'STRATEGIQUE' | 'OPERATIONNELLE' | 'MANAGERIALE' | 'JURIDIQUE';
type Urgence = 'URGENT' | 'NORMAL' | 'PLANIFIE';
type Livrable = 'CONFIRMATION' | 'PREUVE' | 'DOCUMENT';

interface InstructionType {
  id: string;
  code: string;
  label: string;
  categorie: Categorie;
  urgenceDefaut: Urgence;
  livrableAttendu: Livrable;
  actif: boolean;
}

interface ProofType {
  id: string;
  label: string;
  acceptedFormats: string;
  description: string;
  actif: boolean;
}

type UserRole = 'DG' | 'SECRETAIRE' | 'SUBORDONNE' | 'ADMIN_IT';

interface AppUser {
  id: string;
  username: string;
  nomComplet: string;
  role: UserRole;
  actif: boolean;
}

@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [CommonModule, FormsModule, SignatureAssetsComponent],
  template: `
    <div class="p-6 max-w-6xl mx-auto">
      <h1 class="text-2xl font-bold text-gray-800 mb-6">{{ tabs().length === 1 ? 'Mes Signatures' : 'Paramètres' }}</h1>

      <!-- Tabs -->
      @if (tabs().length > 1) {
        <div class="flex gap-2 mb-6 border-b border-gray-200 overflow-x-auto">
          @for (tab of tabs(); track tab.id) {
            <button (click)="activeTab.set(tab.id)"
                    [class]="activeTab() === tab.id
                      ? 'px-5 py-2.5 text-sm font-medium text-blue-700 border-b-2 border-blue-600 -mb-px whitespace-nowrap'
                      : 'px-5 py-2.5 text-sm font-medium text-gray-500 hover:text-gray-800 whitespace-nowrap'">
              {{ tab.label }}
            </button>
          }
        </div>
      }

      <!-- ── SIGNATURE ASSETS ────────────────────────────────────────────── -->
      @if (activeTab() === 'SIGNATURE_ASSETS') {
        <app-signature-assets></app-signature-assets>
      }

      <!-- ── INSTRUCTION TYPES ───────────────────────────────────────────── -->
      @if (activeTab() === 'INSTRUCTION_TYPES') {
        <div>
          <div class="flex items-center justify-between mb-4">
            <div class="flex gap-2">
              @for (cat of categories; track cat.value) {
                <button (click)="toggleCatFilter(cat.value)"
                        [class]="catFilter().includes(cat.value)
                          ? 'px-3 py-1 text-xs rounded-full font-medium ' + cat.activeClass
                          : 'px-3 py-1 text-xs rounded-full font-medium bg-gray-100 text-gray-500 hover:bg-gray-200'">
                  {{ cat.label }}
                </button>
              }
            </div>
            <button (click)="openItypeModal()"
                    class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700">
              + Nouveau type
            </button>
          </div>

          @if (loadingItypes()) {
            <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
          } @else {
            <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    <th class="px-4 py-3">Label</th>
                    <th class="px-4 py-3">Code</th>
                    <th class="px-4 py-3">Catégorie</th>
                    <th class="px-4 py-3">Urgence défaut</th>
                    <th class="px-4 py-3">Livrable</th>
                    <th class="px-4 py-3">Statut</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (t of filteredItypes(); track t.id) {
                    <tr [class]="t.actif ? '' : 'opacity-50'">
                      <td class="px-4 py-3 font-medium text-gray-800">{{ t.label }}</td>
                      <td class="px-4 py-3 text-gray-500 font-mono text-xs">{{ t.code }}</td>
                      <td class="px-4 py-3">
                        <span [class]="categorieBadge(t.categorie)">{{ categorieLabel(t.categorie) }}</span>
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="urgenceBadge(t.urgenceDefaut)">{{ urgenceLabel(t.urgenceDefaut) }}</span>
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="livrablebadge(t.livrableAttendu)">{{ livrableLabel(t.livrableAttendu) }}</span>
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="t.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ t.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openItypeModal(t)"
                                class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="toggleItype(t)"
                                class="text-xs text-gray-500 hover:underline">
                          {{ t.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deleteItype(t)"
                                class="text-xs text-red-500 hover:underline">Supprimer</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (filteredItypes().length === 0) {
                <p class="text-center text-gray-400 text-sm py-8">Aucun type correspondant au filtre.</p>
              }
            </div>
          }
        </div>
      }

      <!-- ── PROOF TYPES ─────────────────────────────────────────────────── -->
      @if (activeTab() === 'PROOF_TYPES') {
        <div>
          <div class="flex justify-end mb-4">
            <button (click)="openPtypeModal()"
                    class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700">
              + Nouveau type de preuve
            </button>
          </div>

          @if (loadingPtypes()) {
            <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
          } @else {
            <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    <th class="px-4 py-3">Label</th>
                    <th class="px-4 py-3">Formats acceptés</th>
                    <th class="px-4 py-3">Description</th>
                    <th class="px-4 py-3">Statut</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (p of proofTypes(); track p.id) {
                    <tr [class]="p.actif ? '' : 'opacity-50'">
                      <td class="px-4 py-3 font-medium text-gray-800">{{ p.label }}</td>
                      <td class="px-4 py-3 text-gray-500 font-mono text-xs">{{ p.acceptedFormats }}</td>
                      <td class="px-4 py-3 text-gray-600 text-xs max-w-xs truncate">{{ p.description }}</td>
                      <td class="px-4 py-3">
                        <span [class]="p.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ p.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openPtypeModal(p)"
                                class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="togglePtype(p)"
                                class="text-xs text-gray-500 hover:underline">
                          {{ p.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deletePtype(p)"
                                class="text-xs text-red-500 hover:underline">Supprimer</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (proofTypes().length === 0) {
                <p class="text-center text-gray-400 text-sm py-8">Aucun type de preuve enregistré.</p>
              }
            </div>
          }
        </div>
      }
      <!-- ── USERS ─────────────────────────────────────────────────────────── -->
      @if (activeTab() === 'USERS') {
        <div>
          <div class="flex items-center justify-between mb-4">
            <p class="text-sm text-gray-500">{{ users().length }} membre(s) enregistré(s)</p>
            <button (click)="openUserModal()"
                    class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700">
              + Nouveau membre
            </button>
          </div>

          @if (loadingUsers()) {
            <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
          } @else {
            <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    <th class="px-4 py-3">Nom complet</th>
                    <th class="px-4 py-3">Identifiant</th>
                    <th class="px-4 py-3">Rôle</th>
                    <th class="px-4 py-3">Statut</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (u of users(); track u.id) {
                    <tr [class]="u.actif ? '' : 'opacity-50'">
                      <td class="px-4 py-3 font-medium text-gray-800">{{ u.nomComplet }}</td>
                      <td class="px-4 py-3 text-gray-500 font-mono text-xs">{{ u.username }}</td>
                      <td class="px-4 py-3">
                        <span [class]="roleBadge(u.role)">{{ roleLabel(u.role) }}</span>
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="u.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ u.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openUserModal(u)"
                                class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="openResetPasswordModal(u)"
                                class="text-xs text-amber-600 hover:underline">Réinit. MDP</button>
                        <button (click)="toggleUser(u)"
                                class="text-xs text-gray-500 hover:underline">
                          {{ u.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deleteUser(u)"
                                class="text-xs text-red-500 hover:underline">Supprimer</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (users().length === 0) {
                <p class="text-center text-gray-400 text-sm py-8">Aucun utilisateur enregistré.</p>
              }
            </div>
          }
        </div>
      }
    </div>

    <!-- ── Modal Instruction Type ─────────────────────────────────────────── -->
    @if (showItypeModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-lg">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingItype ? 'Modifier le type' : 'Nouveau type d\'instruction' }}
          </h2>
          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Code interne</label>
                <input [(ngModel)]="itypeForm['code']" placeholder="EX: AUDIT_RECETTES"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono uppercase" />
              </div>
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Catégorie</label>
                <select [(ngModel)]="itypeForm['categorie']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                  <option value="STRATEGIQUE">Stratégique</option>
                  <option value="OPERATIONNELLE">Opérationnelle</option>
                  <option value="MANAGERIALE">Managériale</option>
                  <option value="JURIDIQUE">Juridique</option>
                </select>
              </div>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Libellé</label>
              <input [(ngModel)]="itypeForm['label']" placeholder="Intitulé du type d'instruction"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Urgence par défaut</label>
                <select [(ngModel)]="itypeForm['urgenceDefaut']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                  <option value="URGENT">Urgent</option>
                  <option value="NORMAL">Normal</option>
                  <option value="PLANIFIE">Planifié</option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Livrable attendu</label>
                <select [(ngModel)]="itypeForm['livrableAttendu']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                  <option value="CONFIRMATION">Confirmation simple</option>
                  <option value="PREUVE">Preuve (photo/PV/scan)</option>
                  <option value="DOCUMENT">Document à signer</option>
                </select>
              </div>
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showItypeModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="saveItype()" [disabled]="savingItype()"
                    class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm disabled:opacity-50">
              {{ savingItype() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal User ───────────────────────────────────────────────────── -->
    @if (showUserModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingUser ? 'Modifier le membre' : 'Nouveau membre' }}
          </h2>
          <div class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Nom complet</label>
              <input [(ngModel)]="userForm['nomComplet']" placeholder="Prénom Nom"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Identifiant de connexion</label>
              <input [(ngModel)]="userForm['username']" placeholder="nom.prenom"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Rôle</label>
              <select [(ngModel)]="userForm['role']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                <option value="DG">DG — Directeur Général</option>
                <option value="SECRETAIRE">Secrétaire</option>
                <option value="SUBORDONNE">Subordonné</option>
                <option value="ADMIN_IT">Administrateur IT</option>
              </select>
            </div>
            @if (!editingUser) {
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Mot de passe initial</label>
                <input [(ngModel)]="userForm['password']" type="password" placeholder="••••••••"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
              </div>
            }
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showUserModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="saveUser()" [disabled]="savingUser()"
                    class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm disabled:opacity-50">
              {{ savingUser() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal Réinitialiser MDP ────────────────────────────────────────── -->
    @if (showResetModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm">
          <h2 class="text-lg font-semibold mb-2">Réinitialiser le mot de passe</h2>
          <p class="text-sm text-gray-500 mb-4">Compte : <strong>{{ resetTargetUser?.nomComplet }}</strong></p>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Nouveau mot de passe</label>
            <input [(ngModel)]="newPasswordValue" type="password" placeholder="••••••••"
                   class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showResetModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="confirmResetPassword()" [disabled]="resettingPassword()"
                    class="px-4 py-2 bg-amber-600 text-white rounded-lg text-sm disabled:opacity-50">
              {{ resettingPassword() ? 'Mise à jour…' : 'Réinitialiser' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal Proof Type ───────────────────────────────────────────────── -->
    @if (showPtypeModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingPtype ? 'Modifier le type de preuve' : 'Nouveau type de preuve' }}
          </h2>
          <div class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Libellé</label>
              <input [(ngModel)]="ptypeForm['label']" placeholder="Ex: Procès-verbal (PV)"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Formats acceptés</label>
              <input [(ngModel)]="ptypeForm['acceptedFormats']" placeholder="Ex: PDF, JPG, PNG"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Description</label>
              <textarea [(ngModel)]="ptypeForm['description']" rows="2"
                        placeholder="Description courte du type de preuve"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none"></textarea>
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showPtypeModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="savePtype()" [disabled]="savingPtype()"
                    class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm disabled:opacity-50">
              {{ savingPtype() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ParametresComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  tabs = computed(() => {
    const role = this.auth.currentUser()?.role;
    if (role === 'SUBORDONNE' || role === 'SECRETAIRE') {
      return [{ id: 'SIGNATURE_ASSETS' as Tab, label: 'Mes Signatures' }];
    }
    return [
      { id: 'INSTRUCTION_TYPES' as Tab, label: "Types d'Instructions" },
      { id: 'PROOF_TYPES' as Tab, label: 'Types de Preuves' },
      { id: 'USERS' as Tab, label: 'Utilisateurs' },
      { id: 'SIGNATURE_ASSETS' as Tab, label: 'Signatures & Cachets' }
    ];
  });

  activeTab = signal<Tab>('INSTRUCTION_TYPES');

  // Instruction Types state
  instructionTypes = signal<InstructionType[]>([]);
  loadingItypes = signal(false);
  showItypeModal = signal(false);
  savingItype = signal(false);
  editingItype: InstructionType | null = null;
  itypeForm: Record<string, string> = {};

  catFilter = signal<Categorie[]>([]);

  filteredItypes = computed(() => {
    const filter = this.catFilter();
    const all = this.instructionTypes();
    return filter.length === 0 ? all : all.filter(t => filter.includes(t.categorie));
  });

  // Users state
  users = signal<AppUser[]>([]);
  loadingUsers = signal(false);
  showUserModal = signal(false);
  savingUser = signal(false);
  editingUser: AppUser | null = null;
  userForm: Record<string, string> = {};

  showResetModal = signal(false);
  resettingPassword = signal(false);
  resetTargetUser: AppUser | null = null;
  newPasswordValue = '';

  // Proof Types state
  proofTypes = signal<ProofType[]>([]);
  loadingPtypes = signal(false);
  showPtypeModal = signal(false);
  savingPtype = signal(false);
  editingPtype: ProofType | null = null;
  ptypeForm: Record<string, string> = {};

  categories = [
    { value: 'STRATEGIQUE' as Categorie,    label: 'Stratégique',    activeClass: 'bg-purple-100 text-purple-700' },
    { value: 'OPERATIONNELLE' as Categorie, label: 'Opérationnelle', activeClass: 'bg-red-100 text-red-700' },
    { value: 'MANAGERIALE' as Categorie,    label: 'Managériale',    activeClass: 'bg-blue-100 text-blue-700' },
    { value: 'JURIDIQUE' as Categorie,      label: 'Juridique',      activeClass: 'bg-amber-100 text-amber-700' },
  ];

  ngOnInit() {
    if (this.tabs().length === 1) {
      this.activeTab.set('SIGNATURE_ASSETS');
    }
    this.loadItypes();
    this.loadPtypes();
    this.loadUsers();
  }

  // ── Instruction Types ─────────────────────────────────────────────────────

  loadItypes() {
    this.loadingItypes.set(true);
    this.api.getInstructionTypes().subscribe({
      next: list => { this.instructionTypes.set(list); this.loadingItypes.set(false); },
      error: () => this.loadingItypes.set(false)
    });
  }

  toggleCatFilter(cat: Categorie) {
    this.catFilter.update(f => f.includes(cat) ? f.filter(c => c !== cat) : [...f, cat]);
  }

  openItypeModal(t?: InstructionType) {
    this.editingItype = t ?? null;
    this.itypeForm = {
      code: t?.code ?? '',
      label: t?.label ?? '',
      categorie: t?.categorie ?? 'OPERATIONNELLE',
      urgenceDefaut: t?.urgenceDefaut ?? 'NORMAL',
      livrableAttendu: t?.livrableAttendu ?? 'CONFIRMATION',
    };
    this.showItypeModal.set(true);
  }

  saveItype() {
    if (!this.itypeForm['label'] || !this.itypeForm['code']) return;
    this.savingItype.set(true);
    const obs = this.editingItype
      ? this.api.updateInstructionType(this.editingItype.id, this.itypeForm)
      : this.api.createInstructionType(this.itypeForm);
    obs.subscribe({
      next: saved => {
        if (this.editingItype) {
          this.instructionTypes.update(list => list.map(x => x.id === saved.id ? saved : x));
        } else {
          this.instructionTypes.update(list => [...list, saved]);
        }
        this.savingItype.set(false);
        this.showItypeModal.set(false);
      },
      error: () => this.savingItype.set(false)
    });
  }

  toggleItype(t: InstructionType) {
    this.api.toggleInstructionType(t.id).subscribe(() => {
      this.instructionTypes.update(list => list.map(x => x.id === t.id ? { ...x, actif: !x.actif } : x));
    });
  }

  deleteItype(t: InstructionType) {
    if (!confirm(`Supprimer définitivement "${t.label}" ?`)) return;
    this.api.deleteInstructionType(t.id).subscribe(() => {
      this.instructionTypes.update(list => list.filter(x => x.id !== t.id));
    });
  }

  // ── Proof Types ───────────────────────────────────────────────────────────

  loadPtypes() {
    this.loadingPtypes.set(true);
    this.api.getProofTypes().subscribe({
      next: list => { this.proofTypes.set(list); this.loadingPtypes.set(false); },
      error: () => this.loadingPtypes.set(false)
    });
  }

  openPtypeModal(p?: ProofType) {
    this.editingPtype = p ?? null;
    this.ptypeForm = {
      label: p?.label ?? '',
      acceptedFormats: p?.acceptedFormats ?? '',
      description: p?.description ?? '',
    };
    this.showPtypeModal.set(true);
  }

  savePtype() {
    if (!this.ptypeForm['label']) return;
    this.savingPtype.set(true);
    const obs = this.editingPtype
      ? this.api.updateProofType(this.editingPtype.id, this.ptypeForm)
      : this.api.createProofType(this.ptypeForm);
    obs.subscribe({
      next: saved => {
        if (this.editingPtype) {
          this.proofTypes.update(list => list.map(x => x.id === saved.id ? saved : x));
        } else {
          this.proofTypes.update(list => [...list, saved]);
        }
        this.savingPtype.set(false);
        this.showPtypeModal.set(false);
      },
      error: () => this.savingPtype.set(false)
    });
  }

  togglePtype(p: ProofType) {
    this.api.toggleProofType(p.id).subscribe(() => {
      this.proofTypes.update(list => list.map(x => x.id === p.id ? { ...x, actif: !x.actif } : x));
    });
  }

  deletePtype(p: ProofType) {
    if (!confirm(`Supprimer définitivement "${p.label}" ?`)) return;
    this.api.deleteProofType(p.id).subscribe(() => {
      this.proofTypes.update(list => list.filter(x => x.id !== p.id));
    });
  }

  // ── Users ─────────────────────────────────────────────────────────────────

  loadUsers() {
    this.loadingUsers.set(true);
    this.api.getUsers().subscribe({
      next: list => { this.users.set(list); this.loadingUsers.set(false); },
      error: () => this.loadingUsers.set(false)
    });
  }

  openUserModal(u?: AppUser) {
    this.editingUser = u ?? null;
    this.userForm = {
      nomComplet: u?.nomComplet ?? '',
      username: u?.username ?? '',
      role: u?.role ?? 'SUBORDONNE',
      password: '',
    };
    this.showUserModal.set(true);
  }

  saveUser() {
    if (!this.userForm['nomComplet'] || !this.userForm['username']) return;
    if (!this.editingUser && !this.userForm['password']) return;
    this.savingUser.set(true);
    const obs = this.editingUser
      ? this.api.updateUser(this.editingUser.id, this.userForm)
      : this.api.createUser(this.userForm);
    obs.subscribe({
      next: saved => {
        if (this.editingUser) {
          this.users.update(list => list.map(x => x.id === saved.id ? saved : x));
        } else {
          this.users.update(list => [...list, saved]);
        }
        this.savingUser.set(false);
        this.showUserModal.set(false);
      },
      error: () => this.savingUser.set(false)
    });
  }

  openResetPasswordModal(u: AppUser) {
    this.resetTargetUser = u;
    this.newPasswordValue = '';
    this.showResetModal.set(true);
  }

  confirmResetPassword() {
    if (!this.resetTargetUser || !this.newPasswordValue.trim()) return;
    this.resettingPassword.set(true);
    this.api.resetUserPassword(this.resetTargetUser.id, this.newPasswordValue).subscribe({
      next: () => { this.resettingPassword.set(false); this.showResetModal.set(false); },
      error: () => this.resettingPassword.set(false)
    });
  }

  toggleUser(u: AppUser) {
    this.api.toggleUser(u.id).subscribe(() => {
      this.users.update(list => list.map(x => x.id === u.id ? { ...x, actif: !x.actif } : x));
    });
  }

  deleteUser(u: AppUser) {
    if (!confirm(`Supprimer définitivement "${u.nomComplet}" ?`)) return;
    this.api.deleteUser(u.id).subscribe(() => {
      this.users.update(list => list.filter(x => x.id !== u.id));
    });
  }

  roleLabel(r: UserRole): string {
    return ({ DG: 'DG', SECRETAIRE: 'Secrétaire', SUBORDONNE: 'Subordonné', ADMIN_IT: 'Admin IT' } as Record<UserRole, string>)[r];
  }

  roleBadge(r: UserRole): string {
    return ({
      DG:         'px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700',
      SECRETAIRE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-700',
      SUBORDONNE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600',
      ADMIN_IT:   'px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700',
    } as Record<UserRole, string>)[r];
  }

  // ── Display helpers ───────────────────────────────────────────────────────

  categorieLabel(c: Categorie): string {
    return ({ STRATEGIQUE: 'Stratégique', OPERATIONNELLE: 'Opérationnelle', MANAGERIALE: 'Managériale', JURIDIQUE: 'Juridique' } as Record<Categorie, string>)[c];
  }

  categorieBadge(c: Categorie): string {
    return ({
      STRATEGIQUE:    'px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-700',
      OPERATIONNELLE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700',
      MANAGERIALE:    'px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700',
      JURIDIQUE:      'px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700',
    } as Record<Categorie, string>)[c];
  }

  urgenceLabel(u: Urgence): string {
    return ({ URGENT: 'Urgent', NORMAL: 'Normal', PLANIFIE: 'Planifié' } as Record<Urgence, string>)[u];
  }

  urgenceBadge(u: Urgence): string {
    return ({
      URGENT:   'px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-600',
      NORMAL:   'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600',
      PLANIFIE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700',
    } as Record<Urgence, string>)[u];
  }

  livrableLabel(l: Livrable): string {
    return ({ CONFIRMATION: 'Confirmation', PREUVE: 'Preuve', DOCUMENT: 'Document' } as Record<Livrable, string>)[l];
  }

  livrablebadge(l: Livrable): string {
    return ({
      CONFIRMATION: 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600',
      PREUVE:       'px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700',
      DOCUMENT:     'px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700',
    } as Record<Livrable, string>)[l];
  }
}
