import { Component, signal, inject, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { SignatureAssetsComponent } from '../signature-assets/signature-assets.component';

type Tab = 'INSTRUCTION_TYPES' | 'PROOF_TYPES' | 'POSTES' | 'USERS' | 'SIGNATURE_ASSETS' | 'TYPE_DOCUMENTS';

type Categorie = 'STRATEGIQUE' | 'OPERATIONNELLE' | 'MANAGERIALE' | 'JURIDIQUE';
type Urgence = 'URGENT' | 'NORMAL' | 'PLANIFIE';
type Livrable = 'CONFIRMATION' | 'PREUVE' | 'DOCUMENT';
type UserRole = 'DG' | 'SECRETAIRE' | 'SUBORDONNE' | 'ADMIN_IT';

interface InstructionType {
  id: string; code: string; label: string;
  categorie: Categorie; urgenceDefaut: Urgence; livrableAttendu: Livrable;
  documentsAttendus: string[]; actif: boolean;
}
interface ProofType {
  id: string; label: string; acceptedFormats: string; description: string; actif: boolean;
}
interface AppUser {
  id: string; username: string; nomComplet: string;
  role: UserRole | null; actif: boolean;
  posteId: string | null; posteLibelle: string | null;
}
interface Poste {
  id: string; code: string; libelle: string; actif: boolean;
}
interface TypeDocParam {
  id: string; code: string; libelle: string;
  modeCircuit: 'MANAGER_SEUL' | 'LIBRE' | 'PREDEFINI' | 'PREDEFINI_MODIFIABLE';
  actionFinale: 'ARCHIVER' | 'PUBLIER';
  circuit: { posteId: string; posteLibelle: string }[];
  initiateurPostes: string[];
  requiresSignatureZone: boolean;
  requiresStampZone: boolean;
  requiresDestinataire: boolean;
  actif: boolean;
}
interface EtapeCircuit {
  id: string; stepOrder: number; stepLabel: string;
  requiresSignature: boolean; requiresAttachment: boolean;
  actorInstructions: string | null;
  requiredPoste: { id: string; code: string; libelle: string } | null;
}

@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [CommonModule, FormsModule, SignatureAssetsComponent],
  template: `
    <div class="p-6 max-w-6xl mx-auto">
      <h1 class="text-2xl font-bold text-gray-800 mb-6">{{ tabs().length === 1 ? 'Mes Signatures' : 'Paramètres' }}</h1>

      <!-- Onglets -->
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

      <!-- ── SIGNATURE ASSETS ───────────────────────────────────────────── -->
      @if (activeTab() === 'SIGNATURE_ASSETS') {
        <app-signature-assets></app-signature-assets>
      }

      <!-- ── INSTRUCTION TYPES ──────────────────────────────────────────── -->
      @if (activeTab() === 'INSTRUCTION_TYPES') {
        <div>
          <div class="flex items-center justify-between mb-4">
            <div class="flex gap-2 flex-wrap">
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
                    class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 flex-shrink-0">
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
                    <th class="px-4 py-3">Urgence</th>
                    <th class="px-4 py-3">Livrable</th>
                    <th class="px-4 py-3">Docs attendus</th>
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
                        @if (t.documentsAttendus.length) {
                          <div class="flex flex-wrap gap-1 max-w-[180px]">
                            @for (doc of t.documentsAttendus; track doc) {
                              <span class="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded text-[10px] border border-indigo-100 truncate max-w-[160px]" [title]="doc">{{ doc }}</span>
                            }
                          </div>
                        } @else {
                          <span class="text-gray-300 text-xs">—</span>
                        }
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="t.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ t.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right">
                        <div class="flex justify-end gap-2 flex-wrap">
                          <button (click)="openStepsModal(t)"
                                  class="text-xs text-indigo-600 hover:underline font-medium">⚙️ Circuit</button>
                          <button (click)="openItypeModal(t)"
                                  class="text-xs text-blue-600 hover:underline">Modifier</button>
                          <button (click)="toggleItype(t)"
                                  class="text-xs text-gray-500 hover:underline">
                            {{ t.actif ? 'Désactiver' : 'Activer' }}
                          </button>
                          <button (click)="deleteItype(t)"
                                  class="text-xs text-red-500 hover:underline">Supprimer</button>
                        </div>
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

      <!-- ── TYPE DOCUMENTS ──────────────────────────────────────────────── -->
      @if (activeTab() === 'TYPE_DOCUMENTS') {
        <div>
          <div class="flex items-center justify-between mb-4">
            <p class="text-sm text-gray-500">Configurez les types de documents : circuit de signature, action finale et habilitations.</p>
            <button (click)="openTdocModal()"
                    class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700">
              + Nouveau type
            </button>
          </div>
          @if (loadingTdocs()) {
            <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
          } @else {
            <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    <th class="px-4 py-3">Libellé</th>
                    <th class="px-4 py-3">Code</th>
                    <th class="px-4 py-3">Circuit</th>
                    <th class="px-4 py-3">Action finale</th>
                    <th class="px-4 py-3">Options</th>
                    <th class="px-4 py-3">Statut</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (t of typeDocuments(); track t.id) {
                    <tr [class]="t.actif ? '' : 'opacity-50'">
                      <td class="px-4 py-3 font-medium text-gray-800">{{ t.libelle }}</td>
                      <td class="px-4 py-3 text-gray-500 font-mono text-xs">{{ t.code }}</td>
                      <td class="px-4 py-3">
                        <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700">
                          {{ modeCircuitLabel(t.modeCircuit) }}
                        </span>
                        @if ((t.modeCircuit === 'PREDEFINI' || t.modeCircuit === 'PREDEFINI_MODIFIABLE') && t.circuit.length) {
                          <div class="mt-1 text-[10px] text-gray-400">{{ circuitLabel(t.circuit) }}</div>
                        }
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="t.actionFinale === 'PUBLIER'
                          ? 'px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700'
                          : 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600'">
                          {{ t.actionFinale === 'PUBLIER' ? 'Publier' : 'Archiver' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-xs text-gray-500 space-x-1">
                        @if (t.requiresSignatureZone) { <span class="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded">Signature</span> }
                        @if (t.requiresStampZone) { <span class="px-1.5 py-0.5 bg-purple-50 text-purple-700 rounded">Tampon</span> }
                        @if (t.requiresDestinataire) { <span class="px-1.5 py-0.5 bg-amber-50 text-amber-700 rounded">Destinataire</span> }
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="t.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ t.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openTdocModal(t)"
                                class="text-xs text-blue-600 hover:text-blue-800 px-2 py-1 rounded hover:bg-blue-50">Modifier</button>
                        <button (click)="toggleTdoc(t)"
                                class="text-xs text-amber-600 hover:text-amber-800 px-2 py-1 rounded hover:bg-amber-50">
                          {{ t.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deleteTdoc(t)"
                                class="text-xs text-red-500 hover:text-red-700 px-2 py-1 rounded hover:bg-red-50">Supprimer</button>
                      </td>
                    </tr>
                  }
                  @if (!typeDocuments().length) {
                    <tr><td colspan="7" class="px-4 py-8 text-center text-gray-400 text-sm">Aucun type de document configuré.</td></tr>
                  }
                </tbody>
              </table>
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
                        <button (click)="openPtypeModal(p)" class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="togglePtype(p)" class="text-xs text-gray-500 hover:underline">
                          {{ p.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deletePtype(p)" class="text-xs text-red-500 hover:underline">Supprimer</button>
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

      <!-- ── POSTES & RÔLES ──────────────────────────────────────────────── -->
      @if (activeTab() === 'POSTES') {
        <div>
          <div class="mb-4 p-4 bg-indigo-50 border border-indigo-100 rounded-lg text-sm text-indigo-700">
            Les Postes définissent les acteurs du nouveau moteur de circuit. Chaque étape de circuit peut requérir un Poste spécifique. Assignez ensuite le Poste à un Utilisateur dans l'onglet <strong>Utilisateurs</strong>.
          </div>
          <div class="flex items-center justify-between mb-4">
            <p class="text-sm text-gray-500">{{ postes().length }} poste(s) défini(s)</p>
            <button (click)="openPosteModal()"
                    class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
              + Nouveau Poste
            </button>
          </div>
          @if (loadingPostes()) {
            <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
          } @else {
            <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    <th class="px-4 py-3">Code</th>
                    <th class="px-4 py-3">Libellé</th>
                    <th class="px-4 py-3">Statut</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (p of postes(); track p.id) {
                    <tr [class]="p.actif ? '' : 'opacity-50'">
                      <td class="px-4 py-3 font-mono text-xs text-gray-500 uppercase">{{ p.code }}</td>
                      <td class="px-4 py-3 font-medium text-gray-800">{{ p.libelle }}</td>
                      <td class="px-4 py-3">
                        <span [class]="p.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ p.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openPosteModal(p)" class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="togglePoste(p)" class="text-xs text-gray-500 hover:underline">
                          {{ p.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deletePoste(p)" class="text-xs text-red-500 hover:underline">Supprimer</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (postes().length === 0) {
                <p class="text-center text-gray-400 text-sm py-8">Aucun poste défini. Créez-en un pour configurer les circuits.</p>
              }
            </div>
          }
        </div>
      }

      <!-- ── USERS ──────────────────────────────────────────────────────── -->
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
                    <th class="px-4 py-3">Rôle legacy</th>
                    <th class="px-4 py-3">Poste</th>
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
                        @if (u.role) {
                          <span [class]="roleBadge(u.role)">{{ roleLabel(u.role) }}</span>
                        } @else {
                          <span class="text-gray-300 text-xs">—</span>
                        }
                      </td>
                      <td class="px-4 py-3">
                        @if (u.posteLibelle) {
                          <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-teal-100 text-teal-700">{{ u.posteLibelle }}</span>
                        } @else {
                          <span class="text-gray-300 text-xs">—</span>
                        }
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="u.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ u.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right flex justify-end gap-2">
                        <button (click)="openUserModal(u)" class="text-xs text-blue-600 hover:underline">Modifier</button>
                        <button (click)="openResetPasswordModal(u)" class="text-xs text-amber-600 hover:underline">Réinit. MDP</button>
                        <button (click)="toggleUser(u)" class="text-xs text-gray-500 hover:underline">
                          {{ u.actif ? 'Désactiver' : 'Activer' }}
                        </button>
                        <button (click)="deleteUser(u)" class="text-xs text-red-500 hover:underline">Supprimer</button>
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

    <!-- ── Modal Instruction Type ───────────────────────────────────────── -->
    @if (showItypeModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingItype ? 'Modifier le type' : "Nouveau type d'instruction" }}
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
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-2">Documents attendus <span class="font-normal text-gray-400">(ce que le subordonné doit fournir)</span></label>
              <div class="space-y-1.5 mb-2">
                @for (doc of itypeDocuments(); track doc; let i = $index) {
                  <div class="flex items-center gap-2 px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg">
                    <span class="flex-1 text-sm text-gray-700">{{ doc }}</span>
                    <button (click)="removeItypeDocument(i)" class="text-gray-300 hover:text-red-500 text-lg leading-none">&times;</button>
                  </div>
                }
              </div>
              <div class="flex gap-2">
                <input [(ngModel)]="newDocumentInput" placeholder="Ex: Rapport mensuel signé"
                       (keydown.enter)="addItypeDocument()"
                       class="flex-1 border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-blue-400" />
                <button (click)="addItypeDocument()"
                        class="px-3 py-1.5 bg-blue-50 text-blue-700 border border-blue-200 rounded-lg text-xs font-medium hover:bg-blue-100">+ Ajouter</button>
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

    <!-- ── Modal Étapes de Circuit ──────────────────────────────────────── -->
    @if (showStepsModal()) {
      <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col">

          <div class="px-6 py-4 border-b border-gray-200 bg-indigo-50 flex items-center justify-between flex-shrink-0">
            <div>
              <h2 class="text-base font-semibold text-indigo-900">⚙️ Circuit — {{ activeItypeForSteps?.label }}</h2>
              <p class="text-xs text-indigo-600 mt-0.5">Définissez les étapes du circuit de validation pour ce type d'instruction.</p>
            </div>
            <button (click)="closeStepsModal()" class="text-gray-400 hover:text-gray-700 text-xl leading-none">&times;</button>
          </div>

          <div class="flex-1 overflow-y-auto p-6 space-y-4">

            <!-- Liste des étapes -->
            @if (loadingSteps()) {
              <p class="text-gray-400 text-sm text-center py-4">Chargement des étapes…</p>
            } @else if (etapesCircuit().length === 0) {
              <div class="text-center py-6 text-gray-400">
                <p class="text-sm">Aucune étape définie.</p>
                <p class="text-xs mt-1">Ajoutez une première étape ci-dessous.</p>
              </div>
            } @else {
              <div class="space-y-2">
                @for (etape of etapesCircuit(); track etape.id) {
                  <div class="flex items-center gap-3 p-3 bg-gray-50 border border-gray-200 rounded-lg">
                    <span class="w-7 h-7 rounded-full bg-indigo-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">
                      {{ etape.stepOrder }}
                    </span>
                    <div class="flex-1 min-w-0">
                      <p class="text-sm font-medium text-gray-800">{{ etape.stepLabel }}</p>
                      <div class="flex items-center gap-2 mt-0.5 flex-wrap">
                        @if (etape.requiredPoste) {
                          <span class="text-xs text-indigo-600">👤 {{ etape.requiredPoste.libelle }}</span>
                        }
                        @if (etape.requiresSignature) {
                          <span class="text-xs text-purple-600">✍️ Signature</span>
                        }
                        @if (etape.requiresAttachment) {
                          <span class="text-xs text-blue-600">📎 Pièce jointe</span>
                        }
                        @if (etape.actorInstructions) {
                          <span class="text-xs text-gray-500 truncate max-w-[200px]" [title]="etape.actorInstructions">
                            💬 {{ etape.actorInstructions }}
                          </span>
                        }
                      </div>
                    </div>
                    <div class="flex gap-2 flex-shrink-0">
                      <button (click)="openStepForm(etape)" class="text-xs text-blue-600 hover:underline">Modifier</button>
                      <button (click)="deleteStep(etape)" class="text-xs text-red-500 hover:underline">Supprimer</button>
                    </div>
                  </div>
                }
              </div>
            }

            <!-- Séparateur -->
            <div class="border-t border-dashed border-gray-200 pt-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">
                {{ editingStep ? 'Modifier l\'étape' : 'Ajouter une étape' }}
              </h3>

              <!-- Formulaire d'étape -->
              <div class="space-y-3 bg-gray-50 border border-gray-200 rounded-lg p-4">
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs font-medium text-gray-600 mb-1">Libellé de l'étape *</label>
                    <input [(ngModel)]="stepForm['stepLabel']" placeholder="Ex: Visa du Chef de Service"
                           class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400" />
                  </div>
                  <div>
                    <label class="block text-xs font-medium text-gray-600 mb-1">Ordre</label>
                    <input type="number" [(ngModel)]="stepForm['stepOrder']" min="1"
                           class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400" />
                  </div>
                </div>
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1">Poste requis <span class="font-normal text-gray-400">(optionnel)</span></label>
                  <select [(ngModel)]="stepForm['requiredPosteId']"
                          class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-indigo-400">
                    <option value="">— Aucun poste requis (ouvert à tous)</option>
                    @for (p of postes(); track p.id) {
                      @if (p.actif) {
                        <option [value]="p.id">{{ p.libelle }} ({{ p.code }})</option>
                      }
                    }
                  </select>
                </div>
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1">Instructions pour l'intervenant <span class="font-normal text-gray-400">(optionnel)</span></label>
                  <textarea [(ngModel)]="stepForm['actorInstructions']" rows="2"
                            placeholder="Ex: Vérifier la conformité du dossier avant visa"
                            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:border-indigo-400"></textarea>
                </div>
                <div class="flex items-center gap-4">
                  <label class="flex items-center gap-2 cursor-pointer select-none text-sm text-gray-700">
                    <input type="checkbox" [(ngModel)]="stepForm['requiresSignature']"
                           class="w-4 h-4 accent-indigo-600" />
                    ✍️ Requiert une signature
                  </label>
                  <label class="flex items-center gap-2 cursor-pointer select-none text-sm text-gray-700">
                    <input type="checkbox" [(ngModel)]="stepForm['requiresAttachment']"
                           class="w-4 h-4 accent-indigo-600" />
                    📎 Requiert une pièce jointe
                  </label>
                </div>
                <div class="flex justify-end gap-2 pt-1">
                  @if (editingStep) {
                    <button (click)="annulerEditionStep()" class="px-3 py-1.5 text-xs text-gray-600 hover:text-gray-800">Annuler</button>
                  }
                  <button (click)="saveStep()" [disabled]="savingStep() || !stepForm['stepLabel']"
                          class="px-4 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-medium disabled:opacity-50 hover:bg-indigo-700">
                    {{ savingStep() ? 'Enregistrement…' : (editingStep ? 'Mettre à jour' : '+ Ajouter l\'étape') }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="px-6 py-4 border-t border-gray-200 flex justify-end bg-gray-50 flex-shrink-0">
            <button (click)="closeStepsModal()"
                    class="px-4 py-2 border border-gray-300 text-gray-700 font-medium rounded-lg hover:bg-gray-100 text-sm">
              Fermer
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal Poste ──────────────────────────────────────────────────── -->
    @if (showPosteModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingPoste ? 'Modifier le Poste' : 'Nouveau Poste' }}
          </h2>
          <div class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Code interne *</label>
              <input [(ngModel)]="posteForm['code']" placeholder="Ex: DAF, DRH, COORD"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:border-indigo-400" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Libellé *</label>
              <input [(ngModel)]="posteForm['libelle']" placeholder="Ex: Directeur Administratif et Financier"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400" />
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showPosteModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="savePoste()" [disabled]="savingPoste()"
                    class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm disabled:opacity-50 hover:bg-indigo-700">
              {{ savingPoste() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal User ────────────────────────────────────────────────────── -->
    @if (showUserModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
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
              <label class="block text-xs font-medium text-gray-600 mb-1">Rôle legacy <span class="font-normal text-gray-400">(laisser vide pour le nouveau modèle)</span></label>
              <select [(ngModel)]="userForm['role']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                <option value="">— Nouveau modèle (Poste uniquement)</option>
                <option value="DG">DG — Directeur Général</option>
                <option value="SECRETAIRE">Secrétaire</option>
                <option value="SUBORDONNE">Subordonné</option>
                <option value="ADMIN_IT">Administrateur IT</option>
              </select>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Poste assigné <span class="font-normal text-gray-400">(nouveau modèle — optionnel)</span></label>
              <select [(ngModel)]="userForm['posteId']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                <option value="">— Aucun poste</option>
                @for (p of postes(); track p.id) {
                  @if (p.actif) {
                    <option [value]="p.id">{{ p.libelle }} ({{ p.code }})</option>
                  }
                }
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

    <!-- ── Modal Réinitialiser MDP ────────────────────────────────────── -->
    @if (showResetModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
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

    <!-- ── Modal Type Document ─────────────────────────────────────────── -->
    @if (showTdocModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-2xl my-4">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingTdoc ? 'Modifier le type de document' : 'Nouveau type de document' }}
          </h2>
          <div class="space-y-4">

            <!-- Code & Libellé -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Code technique <span class="text-red-500">*</span></label>
                <input [(ngModel)]="tdocForm['code']" placeholder="Ex: NOTE_SERVICE"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm uppercase"
                       (input)="tdocForm['code'] = $any($event.target).value.toUpperCase()" />
              </div>
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Libellé affiché <span class="text-red-500">*</span></label>
                <input [(ngModel)]="tdocForm['libelle']" placeholder="Ex: Note de service"
                       class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>

            <!-- Mode circuit & Action finale -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Mode de circuit</label>
                <select [(ngModel)]="tdocForm['modeCircuit']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                  <option value="MANAGER_SEUL">Manager direct uniquement (automatique)</option>
                  <option value="LIBRE">Libre — picker complet à la soumission</option>
                  <option value="PREDEFINI">Circuit fixé — résolu automatiquement</option>
                  <option value="PREDEFINI_MODIFIABLE">Circuit pré-rempli modifiable</option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">Action après signature finale</label>
                <select [(ngModel)]="tdocForm['actionFinale']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                  <option value="ARCHIVER">Archiver (statut Signé)</option>
                  <option value="PUBLIER">Publier (visible dans les notes de service)</option>
                </select>
              </div>
            </div>

            <!-- Circuit pré-défini (visible si PREDEFINI ou PREDEFINI_MODIFIABLE) -->
            @if (tdocForm['modeCircuit'] === 'PREDEFINI' || tdocForm['modeCircuit'] === 'PREDEFINI_MODIFIABLE') {
              <div class="border border-gray-200 rounded-lg p-4">
                <div class="flex items-center justify-between mb-2">
                  <label class="text-xs font-medium text-gray-700">Circuit de signature (dans l'ordre)</label>
                  <button (click)="ajouterEtapeCircuit()" type="button"
                          class="text-xs text-blue-600 hover:text-blue-800 px-2 py-1 rounded hover:bg-blue-50">
                    + Ajouter une étape
                  </button>
                </div>
                @if (!tdocCircuit().length) {
                  <p class="text-xs text-gray-400 italic">Aucune étape définie.</p>
                }
                @for (etape of tdocCircuit(); track $index; let i = $index) {
                  <div class="flex items-center gap-2 mb-2">
                    <span class="text-xs text-gray-400 w-5 text-right">{{ i + 1 }}.</span>
                    <select [(ngModel)]="etape.posteId"
                            (ngModelChange)="onEtapePosteChange(etape, $event)"
                            class="flex-1 border border-gray-200 rounded px-2 py-1.5 text-sm bg-white">
                      <option value="">— Sélectionner un poste</option>
                      @for (p of postes(); track p.id) {
                        @if (p.actif) {
                          <option [value]="p.id">{{ p.libelle }}</option>
                        }
                      }
                    </select>
                    <button (click)="monterEtapeCircuit(i)" [disabled]="i === 0" type="button"
                            class="text-gray-400 hover:text-gray-700 disabled:opacity-30 px-1">↑</button>
                    <button (click)="supprimerEtapeCircuit(i)" type="button"
                            class="text-red-400 hover:text-red-600 px-1 text-sm">✕</button>
                  </div>
                }
              </div>
            }

            <!-- Postes initiateurs -->
            <div class="border border-gray-200 rounded-lg p-4">
              <label class="text-xs font-medium text-gray-700 block mb-2">
                Postes habilités à créer ce type
                <span class="font-normal text-gray-400 ml-1">(laisser vide = tous les utilisateurs)</span>
              </label>
              <div class="flex flex-wrap gap-2">
                @for (p of postes(); track p.id) {
                  @if (p.actif) {
                    <button type="button" (click)="toggleInitiateur(p.id)"
                            [class]="tdocInitiateurs().includes(p.id)
                              ? 'px-3 py-1 text-xs rounded-full bg-blue-600 text-white'
                              : 'px-3 py-1 text-xs rounded-full bg-gray-100 text-gray-600 hover:bg-gray-200'">
                      {{ p.libelle }}
                    </button>
                  }
                }
              </div>
            </div>

            <!-- Options supplémentaires -->
            <div class="grid grid-cols-3 gap-3">
              <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" [(ngModel)]="tdocForm['requiresSignatureZone']"
                       class="rounded border-gray-300" />
                <span class="text-sm text-gray-700">Zone de signature obligatoire</span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" [(ngModel)]="tdocForm['requiresStampZone']"
                       class="rounded border-gray-300" />
                <span class="text-sm text-gray-700">Tampon obligatoire</span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" [(ngModel)]="tdocForm['requiresDestinataire']"
                       class="rounded border-gray-300" />
                <span class="text-sm text-gray-700">Destinataire obligatoire</span>
              </label>
            </div>

          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showTdocModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="saveTdoc()" [disabled]="savingTdoc()"
                    class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm disabled:opacity-50">
              {{ savingTdoc() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal Proof Type ──────────────────────────────────────────────── -->
    @if (showPtypeModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
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
      { id: 'TYPE_DOCUMENTS' as Tab, label: 'Types de Documents' },
      { id: 'PROOF_TYPES' as Tab, label: 'Types de Preuves' },
      { id: 'POSTES' as Tab, label: 'Postes & Rôles' },
      { id: 'USERS' as Tab, label: 'Utilisateurs' },
      { id: 'SIGNATURE_ASSETS' as Tab, label: 'Signatures & Cachets' }
    ];
  });

  activeTab = signal<Tab>('INSTRUCTION_TYPES');

  // ── Instruction Types ────────────────────────────────────────────────────
  instructionTypes = signal<InstructionType[]>([]);
  loadingItypes    = signal(false);
  showItypeModal   = signal(false);
  savingItype      = signal(false);
  editingItype: InstructionType | null = null;
  itypeForm: Record<string, string> = {};
  itypeDocuments   = signal<string[]>([]);
  newDocumentInput = '';
  catFilter        = signal<Categorie[]>([]);

  filteredItypes = computed(() => {
    const filter = this.catFilter();
    const all    = this.instructionTypes();
    return filter.length === 0 ? all : all.filter(t => filter.includes(t.categorie));
  });

  // ── Étapes de circuit ────────────────────────────────────────────────────
  showStepsModal       = signal(false);
  etapesCircuit        = signal<EtapeCircuit[]>([]);
  loadingSteps         = signal(false);
  savingStep           = signal(false);
  editingStep: EtapeCircuit | null = null;
  activeItypeForSteps: InstructionType | null = null;
  stepForm: Record<string, any> = {};

  // ── Postes ───────────────────────────────────────────────────────────────
  postes        = signal<Poste[]>([]);
  loadingPostes = signal(false);
  showPosteModal = signal(false);
  savingPoste   = signal(false);
  editingPoste: Poste | null = null;
  posteForm: Record<string, string> = {};

  // ── Users ────────────────────────────────────────────────────────────────
  users        = signal<AppUser[]>([]);
  loadingUsers = signal(false);
  showUserModal = signal(false);
  savingUser   = signal(false);
  editingUser: AppUser | null = null;
  userForm: Record<string, string> = {};

  showResetModal     = signal(false);
  resettingPassword  = signal(false);
  resetTargetUser: AppUser | null = null;
  newPasswordValue = '';

  // ── Types de Documents ───────────────────────────────────────────────────
  typeDocuments   = signal<TypeDocParam[]>([]);
  loadingTdocs    = signal(false);
  showTdocModal   = signal(false);
  savingTdoc      = signal(false);
  editingTdoc: TypeDocParam | null = null;
  tdocForm: Record<string, any> = {};
  tdocCircuit     = signal<{ posteId: string; posteLibelle: string }[]>([]);
  tdocInitiateurs = signal<string[]>([]);

  // ── Proof Types ──────────────────────────────────────────────────────────
  proofTypes    = signal<ProofType[]>([]);
  loadingPtypes = signal(false);
  showPtypeModal = signal(false);
  savingPtype   = signal(false);
  editingPtype: ProofType | null = null;
  ptypeForm: Record<string, string> = {};

  categories = [
    { value: 'STRATEGIQUE' as Categorie,    label: 'Stratégique',    activeClass: 'bg-purple-100 text-purple-700' },
    { value: 'OPERATIONNELLE' as Categorie, label: 'Opérationnelle', activeClass: 'bg-red-100 text-red-700' },
    { value: 'MANAGERIALE' as Categorie,    label: 'Managériale',    activeClass: 'bg-blue-100 text-blue-700' },
    { value: 'JURIDIQUE' as Categorie,      label: 'Juridique',      activeClass: 'bg-amber-100 text-amber-700' },
  ];

  // ── Lifecycle ────────────────────────────────────────────────────────────

  ngOnInit() {
    if (this.tabs().length === 1) {
      this.activeTab.set('SIGNATURE_ASSETS');
    }
    this.loadItypes();
    this.loadPtypes();
    this.loadPostes();
    this.loadUsers();
    this.loadTdocs();
  }

  // ── Instruction Types ────────────────────────────────────────────────────

  loadItypes() {
    this.loadingItypes.set(true);
    this.api.getInstructionTypes().subscribe({
      next: list => { this.instructionTypes.set(list); this.loadingItypes.set(false); },
      error: ()   => this.loadingItypes.set(false)
    });
  }

  toggleCatFilter(cat: Categorie) {
    this.catFilter.update(f => f.includes(cat) ? f.filter(c => c !== cat) : [...f, cat]);
  }

  openItypeModal(t?: InstructionType) {
    this.editingItype = t ?? null;
    this.itypeForm = {
      code:            t?.code            ?? '',
      label:           t?.label           ?? '',
      categorie:       t?.categorie       ?? 'OPERATIONNELLE',
      urgenceDefaut:   t?.urgenceDefaut   ?? 'NORMAL',
      livrableAttendu: t?.livrableAttendu ?? 'CONFIRMATION',
    };
    this.itypeDocuments.set(t?.documentsAttendus ? [...t.documentsAttendus] : []);
    this.newDocumentInput = '';
    this.showItypeModal.set(true);
  }

  addItypeDocument() {
    const val = this.newDocumentInput.trim();
    if (!val) return;
    this.itypeDocuments.update(docs => [...docs, val]);
    this.newDocumentInput = '';
  }

  removeItypeDocument(index: number) {
    this.itypeDocuments.update(docs => docs.filter((_, i) => i !== index));
  }

  saveItype() {
    if (!this.itypeForm['label'] || !this.itypeForm['code']) return;
    this.savingItype.set(true);
    const payload = { ...this.itypeForm, documentsAttendus: this.itypeDocuments() };
    const obs = this.editingItype
      ? this.api.updateInstructionType(this.editingItype.id, payload)
      : this.api.createInstructionType(payload);
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

  // ── Étapes de circuit ────────────────────────────────────────────────────

  openStepsModal(t: InstructionType) {
    this.activeItypeForSteps = t;
    this.editingStep = null;
    this.resetStepForm();
    this.etapesCircuit.set([]);
    this.showStepsModal.set(true);
    this.loadingSteps.set(true);
    this.api.getWorkflowSteps(t.id).subscribe({
      next: steps => {
        this.etapesCircuit.set(
          [...steps].sort((a: EtapeCircuit, b: EtapeCircuit) => a.stepOrder - b.stepOrder)
        );
        this.resetStepForm();
        this.loadingSteps.set(false);
      },
      error: () => this.loadingSteps.set(false)
    });
  }

  closeStepsModal() {
    this.showStepsModal.set(false);
    this.activeItypeForSteps = null;
    this.editingStep = null;
  }

  openStepForm(etape: EtapeCircuit) {
    this.editingStep = etape;
    this.stepForm = {
      stepLabel:          etape.stepLabel,
      stepOrder:          etape.stepOrder,
      requiredPosteId:    etape.requiredPoste?.id ?? '',
      actorInstructions:  etape.actorInstructions ?? '',
      requiresSignature:  etape.requiresSignature,
      requiresAttachment: etape.requiresAttachment,
    };
  }

  annulerEditionStep() {
    this.editingStep = null;
    this.resetStepForm();
  }

  private resetStepForm() {
    const etapes = this.etapesCircuit();
    const nextOrder = etapes.length > 0
      ? Math.max(...etapes.map(s => s.stepOrder)) + 1
      : 1;
    this.stepForm = {
      stepLabel:          '',
      stepOrder:          nextOrder,
      requiredPosteId:    '',
      actorInstructions:  '',
      requiresSignature:  false,
      requiresAttachment: false,
    };
  }

  saveStep() {
    const typeId = this.activeItypeForSteps?.id;
    if (!typeId || !this.stepForm['stepLabel']) return;
    this.savingStep.set(true);
    const payload = {
      stepLabel:          this.stepForm['stepLabel'],
      stepOrder:          Number(this.stepForm['stepOrder']),
      requiredPosteId:    this.stepForm['requiredPosteId'] || null,
      actorInstructions:  this.stepForm['actorInstructions'] || null,
      requiresSignature:  Boolean(this.stepForm['requiresSignature']),
      requiresAttachment: Boolean(this.stepForm['requiresAttachment']),
    };
    const obs = this.editingStep
      ? this.api.updateWorkflowStep(typeId, this.editingStep.id, payload)
      : this.api.createWorkflowStep(typeId, payload);
    obs.subscribe({
      next: saved => {
        if (this.editingStep) {
          this.etapesCircuit.update(list =>
            list.map(s => s.id === saved.id ? saved : s)
                .sort((a, b) => a.stepOrder - b.stepOrder)
          );
        } else {
          this.etapesCircuit.update(list =>
            [...list, saved].sort((a, b) => a.stepOrder - b.stepOrder)
          );
        }
        this.editingStep = null;
        this.resetStepForm();
        this.savingStep.set(false);
      },
      error: () => this.savingStep.set(false)
    });
  }

  deleteStep(etape: EtapeCircuit) {
    const typeId = this.activeItypeForSteps?.id;
    if (!typeId || !confirm(`Supprimer l'étape "${etape.stepLabel}" ?`)) return;
    this.api.deleteWorkflowStep(typeId, etape.id).subscribe(() => {
      this.etapesCircuit.update(list => list.filter(s => s.id !== etape.id));
    });
  }

  // ── Postes ───────────────────────────────────────────────────────────────

  loadPostes() {
    this.loadingPostes.set(true);
    this.api.getPostes().subscribe({
      next: list => { this.postes.set(list); this.loadingPostes.set(false); },
      error: ()   => this.loadingPostes.set(false)
    });
  }

  openPosteModal(p?: Poste) {
    this.editingPoste = p ?? null;
    this.posteForm = { code: p?.code ?? '', libelle: p?.libelle ?? '' };
    this.showPosteModal.set(true);
  }

  savePoste() {
    if (!this.posteForm['code'] || !this.posteForm['libelle']) return;
    this.savingPoste.set(true);
    const obs = this.editingPoste
      ? this.api.updatePoste(this.editingPoste.id, this.posteForm)
      : this.api.createPoste(this.posteForm);
    obs.subscribe({
      next: saved => {
        if (this.editingPoste) {
          this.postes.update(list => list.map(x => x.id === saved.id ? saved : x));
        } else {
          this.postes.update(list => [...list, saved]);
        }
        this.savingPoste.set(false);
        this.showPosteModal.set(false);
      },
      error: () => this.savingPoste.set(false)
    });
  }

  togglePoste(p: Poste) {
    this.api.togglePoste(p.id).subscribe(() => {
      this.postes.update(list => list.map(x => x.id === p.id ? { ...x, actif: !x.actif } : x));
    });
  }

  deletePoste(p: Poste) {
    if (!confirm(`Supprimer le poste "${p.libelle}" ?`)) return;
    this.api.deletePoste(p.id).subscribe(() => {
      this.postes.update(list => list.filter(x => x.id !== p.id));
    });
  }

  // ── Proof Types ───────────────────────────────────────────────────────────

  loadPtypes() {
    this.loadingPtypes.set(true);
    this.api.getProofTypes().subscribe({
      next: list => { this.proofTypes.set(list); this.loadingPtypes.set(false); },
      error: ()   => this.loadingPtypes.set(false)
    });
  }

  openPtypeModal(p?: ProofType) {
    this.editingPtype = p ?? null;
    this.ptypeForm = {
      label:           p?.label           ?? '',
      acceptedFormats: p?.acceptedFormats ?? '',
      description:     p?.description     ?? '',
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
      error: ()   => this.loadingUsers.set(false)
    });
  }

  openUserModal(u?: AppUser) {
    this.editingUser = u ?? null;
    this.userForm = {
      nomComplet: u?.nomComplet ?? '',
      username:   u?.username   ?? '',
      role:       u?.role       ?? '',
      posteId:    u?.posteId    ?? '',
      password:   '',
    };
    this.showUserModal.set(true);
  }

  saveUser() {
    if (!this.userForm['nomComplet'] || !this.userForm['username']) return;
    if (!this.editingUser && !this.userForm['password']) return;
    this.savingUser.set(true);
    const payload: any = { ...this.userForm };
    payload.role    = payload.role    || null;
    payload.posteId = payload.posteId || null;
    const obs = this.editingUser
      ? this.api.updateUser(this.editingUser.id, payload)
      : this.api.createUser(payload);
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
      next: ()  => { this.resettingPassword.set(false); this.showResetModal.set(false); },
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

  // ── Types de Documents ───────────────────────────────────────────────────

  loadTdocs() {
    this.loadingTdocs.set(true);
    this.api.getTypeDocuments().subscribe({
      next: list => { this.typeDocuments.set(list); this.loadingTdocs.set(false); },
      error: ()   => this.loadingTdocs.set(false),
    });
  }

  openTdocModal(t?: TypeDocParam) {
    this.editingTdoc = t ?? null;
    if (t) {
      this.tdocForm = { code: t.code, libelle: t.libelle, modeCircuit: t.modeCircuit, actionFinale: t.actionFinale };
      this.tdocCircuit.set(t.circuit ? [...t.circuit] : []);
      this.tdocInitiateurs.set(t.initiateurPostes ? [...t.initiateurPostes] : []);
      this.tdocForm['requiresSignatureZone'] = t.requiresSignatureZone;
      this.tdocForm['requiresStampZone'] = t.requiresStampZone;
      this.tdocForm['requiresDestinataire'] = t.requiresDestinataire;
    } else {
      this.tdocForm = { code: '', libelle: '', modeCircuit: 'LIBRE', actionFinale: 'ARCHIVER',
        requiresSignatureZone: true, requiresStampZone: false, requiresDestinataire: false };
      this.tdocCircuit.set([]);
      this.tdocInitiateurs.set([]);
    }
    this.showTdocModal.set(true);
  }

  onEtapePosteChange(etape: { posteId: string; posteLibelle: string }, posteId: string) {
    etape.posteId = posteId;
    etape.posteLibelle = this.postes().find(p => p.id === posteId)?.libelle ?? '';
  }

  ajouterEtapeCircuit() {
    this.tdocCircuit.update(c => [...c, { posteId: '', posteLibelle: '' }]);
  }

  supprimerEtapeCircuit(index: number) {
    this.tdocCircuit.update(c => c.filter((_, i) => i !== index));
  }

  monterEtapeCircuit(index: number) {
    if (index === 0) return;
    const c = [...this.tdocCircuit()];
    [c[index - 1], c[index]] = [c[index], c[index - 1]];
    this.tdocCircuit.set(c);
  }

  toggleInitiateur(posteId: string) {
    const cur = this.tdocInitiateurs();
    this.tdocInitiateurs.set(
      cur.includes(posteId) ? cur.filter(id => id !== posteId) : [...cur, posteId]
    );
  }

  saveTdoc() {
    if (!this.tdocForm['libelle'] || !this.tdocForm['code']) return;
    this.savingTdoc.set(true);
    const circuit = this.tdocCircuit().filter(e => e.posteId);
    const body = {
      ...this.tdocForm,
      circuitJson: circuit,
      initiateurPostesJson: this.tdocInitiateurs(),
    };
    const obs = this.editingTdoc
      ? this.api.updateTypeDocument(this.editingTdoc.id, body)
      : this.api.createTypeDocument(body);
    obs.subscribe({
      next: saved => {
        if (this.editingTdoc) {
          this.typeDocuments.update(list => list.map(x => x.id === saved.id ? saved : x));
        } else {
          this.typeDocuments.update(list => [...list, saved]);
        }
        this.savingTdoc.set(false);
        this.showTdocModal.set(false);
      },
      error: () => this.savingTdoc.set(false),
    });
  }

  toggleTdoc(t: TypeDocParam) {
    this.api.toggleTypeDocument(t.id).subscribe(() => {
      this.typeDocuments.update(list => list.map(x => x.id === t.id ? { ...x, actif: !x.actif } : x));
    });
  }

  deleteTdoc(t: TypeDocParam) {
    if (!confirm(`Supprimer définitivement le type "${t.libelle}" ?`)) return;
    this.api.deleteTypeDocument(t.id).subscribe(() => {
      this.typeDocuments.update(list => list.filter(x => x.id !== t.id));
    });
  }

  circuitLabel(circuit: { posteId: string; posteLibelle: string }[]): string {
    return circuit.map(e => e.posteLibelle).join(' → ');
  }

  modeCircuitLabel(m: string): string {
    return ({
      MANAGER_SEUL: 'Manager direct', LIBRE: 'Libre (picker)',
      PREDEFINI: 'Circuit fixé', PREDEFINI_MODIFIABLE: 'Circuit pré-rempli',
    } as Record<string, string>)[m] ?? m;
  }

  // ── Helpers d'affichage ──────────────────────────────────────────────────

  roleLabel(r: UserRole): string {
    return { DG: 'DG', SECRETAIRE: 'Secrétaire', SUBORDONNE: 'Subordonné', ADMIN_IT: 'Admin IT' }[r] ?? r;
  }

  roleBadge(r: UserRole): string {
    return ({
      DG:         'px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700',
      SECRETAIRE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-700',
      SUBORDONNE: 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600',
      ADMIN_IT:   'px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700',
    } as Record<UserRole, string>)[r] ?? 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600';
  }

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
