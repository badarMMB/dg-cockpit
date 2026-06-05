import { ChangeDetectorRef, Component, signal, inject, OnInit, computed, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { RouterLink } from '@angular/router';
import { SignatureAssetsComponent } from '../signature-assets/signature-assets.component';

/** CSS print injecté à paged.js. Sert d'unique source de vérité visuelle —
 *  sera réutilisé tel quel côté backend (Puppeteer) en Phase 2+ pour générer
 *  un PDF strictement identique à cet aperçu. */
const PREVIEW_PRINT_CSS = `
@page {
  size: A4;
  margin: 2.5cm 2cm;
}
body {
  font-family: Calibri, 'Segoe UI', Arial, sans-serif;
  font-size: 11pt;
  line-height: 1.5;
  color: #111;
}
p { margin: 0 0 0.5em; }
p:empty::before { content: '\\00a0'; }
h1 { font-size: 1.7em; font-weight: 700; margin: 1em 0 0.5em; }
h2 { font-size: 1.4em; font-weight: 700; margin: 0.9em 0 0.4em; }
h3 { font-size: 1.2em; font-weight: 700; margin: 0.8em 0 0.3em; }
h4 { font-size: 1.05em; font-weight: 700; margin: 0.7em 0 0.3em; }
h5, h6 { font-size: 1em; font-weight: 700; margin: 0.6em 0 0.25em; }
ul, ol { margin: 0 0 0.5em 1.5em; padding-left: 1em; }
ul { list-style: disc; }
ol { list-style: decimal; }
li { margin: 0.15em 0; }
table { border-collapse: collapse; width: 100%; margin: 0.5em 0; }
th, td { border: 1px solid #ccc; padding: 0.4em 0.6em; vertical-align: top; }
th { background: #f3f4f6; font-weight: 600; }
img {
  max-width: 100%;
  max-height: 5cm;
  width: auto;
  height: auto;
}
a { color: #00236f; text-decoration: underline; }
strong, b { font-weight: 700; }
em, i { font-style: italic; }
.ql-align-center  { text-align: center; }
.ql-align-right   { text-align: right; }
.ql-align-justify { text-align: justify; }
`;

type Tab = 'INSTRUCTION_TYPES' | 'POSTES' | 'USERS' | 'SIGNATURE_ASSETS' | 'TYPE_DOCUMENTS' | 'WORKFLOWS';

type Categorie = 'STRATEGIQUE' | 'OPERATIONNELLE' | 'MANAGERIALE' | 'JURIDIQUE';
type Urgence = 'URGENT' | 'NORMAL' | 'PLANIFIE';
type UserRole = 'DG' | 'SECRETAIRE' | 'SUBORDONNE' | 'ADMIN_IT';

interface ParticipantTemplateItem {
  id?: string;
  posteId: string;
  posteLibelle: string;
  role: 'RAPPORTEUR' | 'PARTICIPANT' | 'VALIDATEUR' | 'OBSERVATEUR';
  obligatoire: boolean;
  ordre?: number;
}
interface InstructionType {
  id: string; code: string; label: string;
  categorie: Categorie; urgenceDefaut: Urgence;
  typeInstruction: 'LIBRE' | 'DOCUMENTAIRE';
  typeDocumentAttenduId: string | null;
  actif: boolean;
  participantTemplates: ParticipantTemplateItem[];
}
interface AppUser {
  id: string; username: string; nomComplet: string;
  role: UserRole | null; actif: boolean;
  posteId: string | null; posteLibelle: string | null;
  managerId: string | null; managerNomComplet: string | null;
}
interface Poste {
  id: string; code: string; libelle: string; actif: boolean;
  habilitations: string[];
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
  templateHtml: string;
  templateDocxPath: string | null;
  templatePdfPath: string | null;
  linkedInstructionTypeId: string | null;
  workflowDefinitionId: string | null;
  workflowLibelle: string | null;
  actif: boolean;
}

@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [CommonModule, FormsModule, SignatureAssetsComponent, RouterLink],
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
                    <th class="px-4 py-3">Nature</th>
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
                        <span [class]="t.typeInstruction === 'DOCUMENTAIRE'
                          ? 'px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700'
                          : 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600'">
                          {{ t.typeInstruction === 'DOCUMENTAIRE' ? 'Documentaire' : 'Libre' }}
                        </span>
                      </td>
                      <td class="px-4 py-3">
                        <span [class]="t.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                          {{ t.actif ? 'Actif' : 'Inactif' }}
                        </span>
                      </td>
                      <td class="px-4 py-3 text-right">
                        <div class="flex justify-end gap-2 flex-wrap">
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

          <!-- Tableau de gestion : TypeDocument × Flux × Participants -->
          @if (typeDocuments().length > 0) {
            <div class="mt-8">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">Tableau de gestion des flux documentaires</h3>
              <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                      <th class="px-4 py-3">Type de Document</th>
                      <th class="px-4 py-3">Flux déclenché (Instruction)</th>
                      <th class="px-4 py-3">Participants pré-définis</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-100">
                    @for (td of typeDocuments(); track td.id) {
                      <tr [class]="td.actif ? '' : 'opacity-50'">
                        <td class="px-4 py-3">
                          <p class="font-medium text-gray-800">{{ td.libelle }}</p>
                          <p class="text-[10px] text-gray-400 font-mono uppercase">{{ td.code }}</p>
                        </td>
                        <td class="px-4 py-3">
                          @if (td.linkedInstructionTypeId) {
                            <span class="text-indigo-700 text-xs font-medium">
                              {{ instructionTypeLibelle(td.linkedInstructionTypeId) }}
                            </span>
                          } @else {
                            <span class="text-gray-300 text-xs">—</span>
                          }
                        </td>
                        <td class="px-4 py-3">
                          @for (p of participantsForTypeDoc(td.linkedInstructionTypeId); track p.posteId) {
                            <span class="inline-flex items-center gap-1 mr-1 mb-1 px-1.5 py-0.5 rounded text-[10px] font-medium"
                                  [class]="p.role === 'RAPPORTEUR'  ? 'bg-blue-100 text-blue-700'  :
                                            p.role === 'VALIDATEUR'  ? 'bg-green-100 text-green-700' :
                                            p.role === 'OBSERVATEUR' ? 'bg-gray-100 text-gray-600'  :
                                            'bg-amber-100 text-amber-700'">
                              {{ p.posteLibelle || p.posteId }} · {{ p.role }}
                            </span>
                          }
                          @if (!td.linkedInstructionTypeId || participantsForTypeDoc(td.linkedInstructionTypeId).length === 0) {
                            <span class="text-gray-300 text-xs">—</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
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
                    <th class="px-4 py-3">Workflow</th>
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
                      <!-- Colonne Workflow -->
                      <td class="px-4 py-3">
                        @if ($any(t).workflowDefinitionId) {
                          <a [routerLink]="['/parametres/workflow-designer', $any(t).workflowDefinitionId]"
                             class="text-xs text-indigo-600 hover:underline truncate block max-w-[120px]"
                             [title]="$any(t).workflowLibelle">
                            🔄 {{ $any(t).workflowLibelle ?? '…' }}
                          </a>
                        } @else {
                          <button (click)="generateWorkflow($any(t))"
                                  class="text-xs text-gray-400 hover:text-indigo-600 hover:underline">
                            + Créer workflow
                          </button>
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
                      <td class="px-4 py-3">
                        <p class="font-medium text-gray-800">{{ p.libelle }}</p>
                        @if (p.habilitations.length) {
                          <div class="flex flex-wrap gap-1 mt-1">
                            @for (h of p.habilitations; track h) {
                              <span class="px-1.5 py-0.5 rounded text-[10px] font-medium bg-indigo-100 text-indigo-700">
                                {{ habLabel(h) }}
                              </span>
                            }
                          </div>
                        }
                      </td>
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
                    <th class="px-4 py-3">Manager</th>
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
                        @if (u.managerNomComplet) {
                          <span class="text-xs text-gray-600">{{ u.managerNomComplet }}</span>
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
                <label class="block text-xs font-medium text-gray-600 mb-1">Nature</label>
                <select [(ngModel)]="itypeForm['typeInstruction']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                  <option value="LIBRE">Libre (chat sans document attendu)</option>
                  <option value="DOCUMENTAIRE">Documentaire (livrable document)</option>
                </select>
              </div>
            </div>
            @if (itypeForm['typeInstruction'] === 'DOCUMENTAIRE') {
              <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">
                  Type de document attendu
                  <span class="font-normal text-gray-400">(le livrable attendu pour ce type d'instruction)</span>
                </label>
                <select [(ngModel)]="itypeForm['typeDocumentAttenduId']"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-blue-400">
                  <option value="">— Sélectionner un type de document</option>
                  @for (td of typeDocuments(); track td.id) {
                    @if (td.actif) {
                      <option [value]="td.id">{{ td.libelle }} ({{ td.code }})</option>
                    }
                  }
                </select>
              </div>

              <!-- Participants par défaut -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Participants par défaut</p>
                  <button (click)="addParticipantTemplate()"
                          class="text-xs text-indigo-600 hover:text-indigo-800 font-medium">+ Ajouter</button>
                </div>
                <div class="flex flex-col gap-2">
                  @for (p of editingParticipants(); track $index; let i = $index) {
                    <div class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg border border-gray-200">
                      <select [(ngModel)]="p.posteId"
                              class="flex-1 border border-gray-300 rounded px-2 py-1.5 text-xs bg-white">
                        <option value="">— Poste —</option>
                        @for (poste of postes(); track poste.id) {
                          @if (poste.actif) {
                            <option [value]="poste.id">{{ poste.libelle }}</option>
                          }
                        }
                      </select>
                      <select [(ngModel)]="p.role"
                              class="flex-1 border border-gray-300 rounded px-2 py-1.5 text-xs bg-white">
                        @for (r of ROLES_PARTICIPANT; track r.key) {
                          <option [value]="r.key">{{ r.label }}</option>
                        }
                      </select>
                      <label class="flex items-center gap-1 text-[10px] text-gray-500 whitespace-nowrap cursor-pointer">
                        <input type="checkbox" [(ngModel)]="p.obligatoire" class="accent-indigo-600" />
                        Obligatoire
                      </label>
                      <button (click)="removeParticipantTemplate(i)"
                              class="text-red-400 hover:text-red-600 text-xs flex-shrink-0">✕</button>
                    </div>
                  }
                  @if (editingParticipants().length === 0) {
                    <p class="text-xs text-gray-400 italic py-1">
                      Aucun participant pré-défini. Ils seront assignés manuellement à la création.
                    </p>
                  }
                </div>
              </div>
            }
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

    <!-- ── WORKFLOWS ────────────────────────────────────────────────────── -->
    @if (activeTab() === 'WORKFLOWS') {
      <div>
        <div class="flex items-center justify-between mb-4">
          <p class="text-sm text-gray-500">Définissez les workflows métier et leurs étapes.</p>
          <button (click)="openWorkflowModal()"
                  class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
            + Nouveau workflow
          </button>
        </div>

        @if (loadingWorkflows()) {
          <p class="text-gray-400 text-sm py-8 text-center">Chargement…</p>
        } @else {
          <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <table class="w-full text-sm">
              <thead>
                <tr class="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  <th class="px-4 py-3">Code</th>
                  <th class="px-4 py-3">Libellé</th>
                  <th class="px-4 py-3">Version</th>
                  <th class="px-4 py-3">Statut</th>
                  <th class="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                @for (wf of workflows(); track wf.id) {
                  <tr [class]="wf.actif ? '' : 'opacity-50'">
                    <td class="px-4 py-3 font-mono text-xs text-gray-500 uppercase">{{ wf.code }}</td>
                    <td class="px-4 py-3 font-medium text-gray-800">{{ wf.libelle }}</td>
                    <td class="px-4 py-3 text-xs text-gray-500">v{{ wf.version }}</td>
                    <td class="px-4 py-3">
                      <span [class]="wf.actif ? 'text-green-600 text-xs' : 'text-gray-400 text-xs'">
                        {{ wf.actif ? 'Actif' : 'Inactif' }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-right flex justify-end gap-2 flex-wrap">
                      <a [routerLink]="['/parametres/workflow-designer', wf.id]"
                         class="text-xs text-indigo-600 hover:underline font-medium">Designer</a>
                      <button (click)="openWorkflowModal(wf)"
                              class="text-xs text-blue-600 hover:underline">Modifier</button>
                      <button (click)="toggleWorkflow(wf)"
                              class="text-xs text-gray-500 hover:underline">
                        {{ wf.actif ? 'Désactiver' : 'Activer' }}
                      </button>
                      <button (click)="deleteWorkflow(wf)"
                              class="text-xs text-red-500 hover:underline">Supprimer</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            @if (workflows().length === 0) {
              <p class="text-center text-gray-400 text-sm py-8">Aucun workflow défini.</p>
            }
          </div>
        }
      </div>
    }

    <!-- ── Modal Workflow ─────────────────────────────────────────────────── -->
    @if (showWorkflowModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm">
          <h2 class="text-lg font-semibold mb-4">
            {{ editingWorkflow ? 'Modifier le workflow' : 'Nouveau workflow' }}
          </h2>
          <div class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Code *</label>
              <input [(ngModel)]="workflowForm['code']" placeholder="Ex: ORDRE_MISSION"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono uppercase" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Libellé *</label>
              <input [(ngModel)]="workflowForm['libelle']" placeholder="Ex: Circuit Ordre de Mission"
                     class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Description</label>
              <textarea [(ngModel)]="workflowForm['description']" rows="2"
                        placeholder="Description du workflow…"
                        class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none">
              </textarea>
            </div>
          </div>
          <div class="flex justify-end gap-3 mt-6">
            <button (click)="showWorkflowModal.set(false)"
                    class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
            <button (click)="saveWorkflow()" [disabled]="savingWorkflow()"
                    class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm disabled:opacity-50 hover:bg-indigo-700">
              {{ savingWorkflow() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Modal Poste ──────────────────────────────────────────────────── -->
    @if (showPosteModal()) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
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

            <!-- Habilitations -->
            <div>
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Droits & Accès</p>
              <div class="flex flex-col gap-1.5">
                @for (hab of ALL_HABILITATIONS; track hab.key) {
                  <label class="flex items-start gap-3 p-2.5 rounded-lg border cursor-pointer transition-colors"
                         [class]="hasHabilitation(hab.key)
                           ? 'border-indigo-300 bg-indigo-50'
                           : 'border-gray-200 hover:border-gray-300 bg-white'">
                    <input type="checkbox"
                           [checked]="hasHabilitation(hab.key)"
                           (change)="toggleHabilitation(hab.key)"
                           class="mt-0.5 h-4 w-4 accent-indigo-600 flex-shrink-0" />
                    <div class="min-w-0">
                      <p class="text-sm font-medium text-gray-800 leading-snug">{{ hab.label }}</p>
                      <p class="text-xs text-gray-500 leading-snug">{{ hab.description }}</p>
                    </div>
                  </label>
                }
              </div>
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
              <label class="block text-xs font-medium text-gray-600 mb-1">Poste assigné <span class="font-normal text-gray-400">(optionnel)</span></label>
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
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Manager direct <span class="font-normal text-gray-400">(supérieur hiérarchique)</span></label>
              <select [(ngModel)]="userForm['managerId']"
                      class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white">
                <option value="">— Aucun manager</option>
                @for (u of users(); track u.id) {
                  @if (u.actif && u.id !== editingUser?.id) {
                    <option [value]="u.id">{{ u.nomComplet }}{{ u.posteLibelle ? ' · ' + u.posteLibelle : '' }}</option>
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

            <!-- Gabarit .docx : chargement direct (Collabora) -->
            <div class="mt-4 pt-4 border-t border-gray-200">
              <label class="text-sm font-medium text-gray-700 block mb-2">Gabarit Word (.docx)</label>
              @if (templateFileName()) {
                <div class="flex items-center gap-2 text-sm text-green-700 bg-green-50 border border-green-200 rounded-lg px-3 py-2">
                  <span>✅</span>
                  <span class="flex-1 truncate">{{ templateFileName() }}</span>
                  @if (editingTdoc?.id) {
                    <button type="button" (click)="previewTemplate()"
                            [disabled]="previewingTemplate()"
                            class="text-blue-600 hover:text-blue-800 text-xs font-medium disabled:opacity-50">
                      {{ previewingTemplate() ? '…' : '👁 Aperçu' }}
                    </button>
                  }
                  <button type="button" (click)="tdocForm['templateDocxPath'] = null; tdocForm['templatePdfPath'] = null; templateFileName.set(null)"
                          class="text-gray-400 hover:text-red-500 font-bold leading-none">×</button>
                </div>
              } @else {
                <button type="button" (click)="templateInput.click()"
                        [disabled]="uploadingTemplate()"
                        class="btn-secondary btn-sm">
                  @if (uploadingTemplate()) { Chargement… } @else { 📎 Charger un gabarit (.docx) }
                </button>
              }
              <input #templateInput type="file" accept=".docx" hidden
                     (change)="onTemplateUpload($event)" />
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

    <!-- Modal aperçu gabarit .docx converti en PDF -->
    @if (showTemplatePreview()) {
      <div class="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-[70] p-4"
           (click)="closeTemplatePreview()">
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-4xl flex flex-col"
             style="height: 88vh"
             (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between px-5 py-3 border-b border-gray-200 shrink-0">
            <span class="text-sm font-semibold text-gray-800">Aperçu du gabarit — {{ tdocForm['libelle'] || tdocForm['code'] }}</span>
            <button (click)="closeTemplatePreview()" class="text-gray-400 hover:text-gray-700 text-xl leading-none">×</button>
          </div>
          @if (templatePreviewUrl()) {
            <iframe [src]="templatePreviewUrl()"
                    class="flex-1 w-full rounded-b-xl"
                    style="border:none">
            </iframe>
          } @else {
            <div class="flex-1 flex flex-col items-center justify-center gap-3 text-gray-500">
              <svg class="animate-spin w-8 h-8 text-blue-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
              </svg>
              <span class="text-sm">Conversion en cours…</span>
            </div>
          }
        </div>
      </div>
    }

    <!-- Modal Aperçu A4 : HTML autonome (CSS embarquée) servi par /api/pdf/preview
         et rendu dans une iframe srcdoc. Même contrat HTML+CSS que /api/pdf/generate. -->
    @if (showTdocPreview()) {
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-[60] p-4"
           (click)="closeTdocPreview()">
        <div class="bg-gray-200 rounded-lg shadow-2xl max-w-5xl w-full max-h-[90vh] overflow-hidden flex flex-col"
             (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between px-5 py-3 bg-white border-b border-gray-200">
            <h3 class="text-base font-semibold text-gray-800">
              Aperçu A4 — {{ tdocForm['libelle'] || tdocForm['code'] || 'sans titre' }}
            </h3>
            <button (click)="closeTdocPreview()" class="btn-icon" title="Fermer">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div class="flex-1 overflow-auto p-6 bg-gray-200 relative">
            @if (previewing()) {
              <div class="absolute inset-0 flex items-center justify-center bg-gray-200/80 z-10">
                <span class="text-sm text-gray-700 italic">Génération de l'aperçu…</span>
              </div>
            }
            <div class="w-full bg-white" #previewTarget>
              @if (previewHtml()) {
                <iframe #previewIframe
                        [srcdoc]="previewHtml()"
                        (load)="onPreviewIframeLoad(previewIframe)"
                        class="w-full border-0 bg-white block"
                        style="min-height: 400px;"></iframe>
              }
            </div>
          </div>
          <div class="flex justify-end gap-3 px-5 py-3 bg-white border-t border-gray-200">
            <button (click)="closeTdocPreview()" class="btn-secondary btn-sm">
              Fermer l'aperçu
            </button>
            <button (click)="downloadTdocPdf()"
                    [disabled]="!processedTemplateHtml() || downloadingPdf()"
                    class="btn-primary btn-sm disabled:opacity-50"
                    title="Générer et télécharger le PDF final">
              {{ downloadingPdf() ? 'Génération…' : 'Télécharger PDF' }}
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
  private toast = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);
  private sanitizer = inject(DomSanitizer);

  uploadingTemplate = signal(false);
  templateFileName = signal<string | null>(null);
  previewingTemplate = signal(false);
  showTemplatePreview = signal(false);
  templatePreviewUrl = signal<SafeResourceUrl | null>(null);
  private _templatePreviewObjectUrl: string | null = null;
  showTdocPreview = signal(false);
  previewing = signal(false);
  previewHtml = signal<SafeHtml | null>(null);
  processedTemplateHtml = signal<string>('');  // fragment HTML avec images en data: URI (preview + download)
  downloadingPdf = signal(false);
  @ViewChild('previewTarget') previewTarget?: ElementRef<HTMLDivElement>;
  @ViewChild('previewIframe') previewIframeRef?: ElementRef<HTMLIFrameElement>;
  private resizeObserver?: ResizeObserver;
  private static readonly A4_WIDTH_PX = 793; // 210mm @ 96 DPI

  tabs = computed(() => {
    const tabs: { id: Tab; label: string }[] = [];
    if (this.auth.hasPermission('CAN_MANAGE_TYPES')) {
      tabs.push(
        { id: 'INSTRUCTION_TYPES', label: "Types d'Instructions" },
        { id: 'TYPE_DOCUMENTS', label: 'Types de Documents' },
        { id: 'POSTES', label: 'Postes & Roles' },
        { id: 'WORKFLOWS', label: 'Workflows' },
      );
    }
    if (this.auth.hasPermission('CAN_MANAGE_USERS')) {
      tabs.push({ id: 'USERS', label: 'Utilisateurs' });
    }
    tabs.push({ id: 'SIGNATURE_ASSETS', label: 'Signatures & Cachets' });
    return tabs;
  });

  activeTab = signal<Tab>('INSTRUCTION_TYPES');

  // ── Workflows ────────────────────────────────────────────────────────────
  workflows        = signal<any[]>([]);
  loadingWorkflows = signal(false);
  showWorkflowModal = signal(false);
  savingWorkflow   = signal(false);
  editingWorkflow: any = null;
  workflowForm: Record<string, any> = {};

  loadWorkflows() {
    this.loadingWorkflows.set(true);
    this.api.getWorkflows().subscribe({
      next: wfs => { this.workflows.set(wfs); this.loadingWorkflows.set(false); },
      error: ()  => this.loadingWorkflows.set(false),
    });
  }

  openWorkflowModal(wf?: any) {
    this.editingWorkflow = wf ?? null;
    this.workflowForm = {
      code:        wf?.code        ?? '',
      libelle:     wf?.libelle     ?? '',
      description: wf?.description ?? '',
      actif:       wf?.actif       ?? false,
    };
    this.showWorkflowModal.set(true);
  }

  saveWorkflow() {
    if (!this.workflowForm['code'] || !this.workflowForm['libelle']) return;
    this.savingWorkflow.set(true);
    const obs = this.editingWorkflow
      ? this.api.updateWorkflow(this.editingWorkflow.id, this.workflowForm)
      : this.api.createWorkflow(this.workflowForm);
    obs.subscribe({
      next: saved => {
        if (this.editingWorkflow) {
          this.workflows.update(list => list.map(w => w.id === saved.id ? saved : w));
        } else {
          this.workflows.update(list => [...list, saved]);
        }
        this.savingWorkflow.set(false);
        this.showWorkflowModal.set(false);
      },
      error: () => this.savingWorkflow.set(false),
    });
  }

  toggleWorkflow(wf: any) {
    this.api.toggleWorkflow(wf.id).subscribe(saved =>
      this.workflows.update(list => list.map(w => w.id === saved.id ? saved : w))
    );
  }

  deleteWorkflow(wf: any) {
    if (!confirm(`Supprimer définitivement le workflow "${wf.libelle}" ?`)) return;
    this.api.deleteWorkflow(wf.id).subscribe({
      next: () => this.workflows.update(list => list.filter(w => w.id !== wf.id)),
      error: () => alert('Impossible de supprimer ce workflow : des instances sont actives.'),
    });
  }

  // ── Instruction Types ────────────────────────────────────────────────────
  instructionTypes = signal<InstructionType[]>([]);
  loadingItypes    = signal(false);
  showItypeModal   = signal(false);
  savingItype      = signal(false);
  editingItype: InstructionType | null = null;
  itypeForm: Record<string, any> = {};

  // ── Participants par défaut (ParticipantTemplate) ──────────────────────
  editingParticipants = signal<ParticipantTemplateItem[]>([]);

  readonly ROLES_PARTICIPANT: { key: ParticipantTemplateItem['role']; label: string; description: string }[] = [
    { key: 'RAPPORTEUR',  label: 'Rapporteur',  description: 'Responsable principal, rédige le livrable' },
    { key: 'PARTICIPANT', label: 'Participant',  description: 'Contributeur actif' },
    { key: 'VALIDATEUR',  label: 'Validateur',  description: 'Valide le livrable produit' },
    { key: 'OBSERVATEUR', label: 'Observateur', description: 'Consultation uniquement' },
  ];

  addParticipantTemplate() {
    this.editingParticipants.update(list => [
      ...list,
      { posteId: '', posteLibelle: '', role: 'PARTICIPANT' as const, obligatoire: false }
    ]);
  }

  removeParticipantTemplate(i: number) {
    this.editingParticipants.update(list => list.filter((_, idx) => idx !== i));
  }

  participantsForTypeDoc(linkedInstructionTypeId: string | null): ParticipantTemplateItem[] {
    if (!linkedInstructionTypeId) return [];
    const it = this.instructionTypes().find(t => t.id === linkedInstructionTypeId);
    return it?.participantTemplates ?? [];
  }

  instructionTypeLibelle(id: string | null): string {
    if (!id) return '—';
    return this.instructionTypes().find(t => t.id === id)?.label ?? id;
  }
  catFilter        = signal<Categorie[]>([]);

  filteredItypes = computed(() => {
    const filter = this.catFilter();
    const all    = this.instructionTypes();
    return filter.length === 0 ? all : all.filter(t => filter.includes(t.categorie));
  });

  // ── Postes ───────────────────────────────────────────────────────────────
  postes        = signal<Poste[]>([]);
  loadingPostes = signal(false);
  showPosteModal = signal(false);
  savingPoste   = signal(false);
  editingPoste: Poste | null = null;
  posteForm: Record<string, string> = {};
  editingPosteHabilitations = signal<Set<string>>(new Set());

  readonly ALL_HABILITATIONS: { key: string; label: string; description: string }[] = [
    { key: 'CAN_CREATE_INSTRUCTION', label: 'Créer des instructions',      description: 'Initier de nouvelles instructions / dossiers' },
    { key: 'CAN_SIGN',               label: 'Signer des documents',         description: 'Apposer une signature électronique au parapheur' },
    { key: 'CAN_VALIDATE',           label: 'Valider une étape',            description: 'Valider une étape de circuit de signature' },
    { key: 'CAN_REJECT',             label: 'Rejeter une étape',            description: 'Rejeter une étape de circuit' },
    { key: 'CAN_CLOSE',              label: 'Clôturer un dossier',          description: 'Clôturer définitivement une instruction' },
    { key: 'CAN_MANAGE_USERS',       label: 'Gérer les utilisateurs',       description: 'Accès à l\'onglet Utilisateurs dans Paramètres' },
    { key: 'CAN_MANAGE_TYPES',       label: 'Gérer les types & paramètres', description: 'Accès CRUD aux types d\'instructions, documents, postes' },
    { key: 'CAN_VIEW_ALL',           label: 'Vue globale',                  description: 'Voir tous les dossiers (mode superviseur)' },
  ];

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


  categories = [
    { value: 'STRATEGIQUE' as Categorie,    label: 'Stratégique',    activeClass: 'bg-purple-100 text-purple-700' },
    { value: 'OPERATIONNELLE' as Categorie, label: 'Opérationnelle', activeClass: 'bg-red-100 text-red-700' },
    { value: 'MANAGERIALE' as Categorie,    label: 'Managériale',    activeClass: 'bg-blue-100 text-blue-700' },
    { value: 'JURIDIQUE' as Categorie,      label: 'Juridique',      activeClass: 'bg-amber-100 text-amber-700' },
  ];

  // ── Lifecycle ────────────────────────────────────────────────────────────

  ngOnInit() {
    const firstTab = this.tabs()[0]?.id;
    if (firstTab && !this.tabs().some(tab => tab.id === this.activeTab())) {
      this.activeTab.set(firstTab);
    }
    this.loadItypes();
    this.loadPostes();
    this.loadUsers();
    this.loadTdocs();
    this.loadWorkflows();
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
      code:                  t?.code                  ?? '',
      label:                 t?.label                 ?? '',
      categorie:             t?.categorie             ?? 'OPERATIONNELLE',
      urgenceDefaut:         t?.urgenceDefaut         ?? 'NORMAL',
      typeInstruction:       t?.typeInstruction       ?? 'LIBRE',
      typeDocumentAttenduId: t?.typeDocumentAttenduId ?? '',
    };
    this.editingParticipants.set(
      (t?.participantTemplates ?? []).map(p => ({ ...p }))
    );
    this.showItypeModal.set(true);
  }

  saveItype() {
    if (!this.itypeForm['label'] || !this.itypeForm['code']) return;
    this.savingItype.set(true);
    const payload = {
      ...this.itypeForm,
      typeDocumentAttenduId: this.itypeForm['typeDocumentAttenduId'] || null,
    };
    const obs = this.editingItype
      ? this.api.updateInstructionType(this.editingItype.id, payload)
      : this.api.createInstructionType(payload);
    obs.subscribe({
      next: saved => {
        // Sauvegarder les participants (PUT remplace toute la liste)
        const participants = this.editingParticipants()
          .filter(p => p.posteId)
          .map((p, i) => ({ ...p, ordre: i }));
        this.api.saveInstructionTypeParticipants(saved.id, participants).subscribe(updatedPts => {
          const withParticipants = { ...saved, participantTemplates: updatedPts };
          if (this.editingItype) {
            this.instructionTypes.update(list => list.map(x => x.id === saved.id ? withParticipants : x));
          } else {
            this.instructionTypes.update(list => [...list, withParticipants]);
          }
          this.savingItype.set(false);
          this.showItypeModal.set(false);
        });
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
    this.editingPosteHabilitations.set(new Set(p?.habilitations ?? []));
    this.showPosteModal.set(true);
  }

  toggleHabilitation(key: string) {
    this.editingPosteHabilitations.update(s => {
      const next = new Set(s);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  }

  hasHabilitation(key: string): boolean {
    return this.editingPosteHabilitations().has(key);
  }

  habLabel(key: string): string {
    return this.ALL_HABILITATIONS.find(h => h.key === key)?.label ?? key;
  }

  savePoste() {
    if (!this.posteForm['code'] || !this.posteForm['libelle']) return;
    this.savingPoste.set(true);
    const payload = {
      ...this.posteForm,
      habilitations: Array.from(this.editingPosteHabilitations()),
    };
    const obs = this.editingPoste
      ? this.api.updatePoste(this.editingPoste.id, payload)
      : this.api.createPoste(payload);
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
      managerId:  u?.managerId  ?? '',
      password:   '',
    };
    this.showUserModal.set(true);
  }

  saveUser() {
    if (!this.userForm['nomComplet'] || !this.userForm['username']) return;
    if (!this.editingUser && !this.userForm['password']) return;
    this.savingUser.set(true);
    const payload: any = { ...this.userForm };
    payload.role      = payload.role      || null;
    payload.posteId   = payload.posteId   || null;
    payload.managerId = payload.managerId || null;
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
      this.tdocForm = {
        code: t.code, libelle: t.libelle, modeCircuit: t.modeCircuit, actionFinale: t.actionFinale,
        requiresSignatureZone: t.requiresSignatureZone,
        requiresStampZone: t.requiresStampZone,
        requiresDestinataire: t.requiresDestinataire,
        templateHtml: t.templateHtml ?? '',
        templateDocxPath: t.templateDocxPath ?? null,
        templatePdfPath: t.templatePdfPath ?? null,
      };
      this.templateFileName.set(t.templateDocxPath ? t.templateDocxPath.split('/').pop() ?? t.templateDocxPath : null);
      this.tdocCircuit.set(t.circuit ? [...t.circuit] : []);
      this.tdocInitiateurs.set(t.initiateurPostes ? [...t.initiateurPostes] : []);
    } else {
      this.tdocForm = {
        code: '', libelle: '', modeCircuit: 'LIBRE', actionFinale: 'ARCHIVER',
        requiresSignatureZone: true, requiresStampZone: false, requiresDestinataire: false,
        templateHtml: '', templateDocxPath: null, templatePdfPath: null,
      };
      this.templateFileName.set(null);
      this.tdocCircuit.set([]);
      this.tdocInitiateurs.set([]);
    }
    this.showTdocModal.set(true);
  }

  previewTemplate() {
    if (!this.editingTdoc?.id) return;
    this.previewingTemplate.set(true);
    this.templatePreviewUrl.set(null);
    this.showTemplatePreview.set(true);
    this.api.getTemplatePreviewBlob(this.editingTdoc.id).subscribe({
      next: blob => {
        if (this._templatePreviewObjectUrl) URL.revokeObjectURL(this._templatePreviewObjectUrl);
        this._templatePreviewObjectUrl = URL.createObjectURL(blob);
        this.templatePreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this._templatePreviewObjectUrl));
      },
      error: () => { this.toast.error('Aperçu indisponible'); this.closeTemplatePreview(); },
      complete: () => this.previewingTemplate.set(false),
    });
  }

  closeTemplatePreview() {
    this.showTemplatePreview.set(false);
    if (this._templatePreviewObjectUrl) { URL.revokeObjectURL(this._templatePreviewObjectUrl); this._templatePreviewObjectUrl = null; }
    this.templatePreviewUrl.set(null);
  }

  onTemplateUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingTemplate.set(true);
    this.api.uploadTemplate(file).subscribe({
      next: r => {
        this.tdocForm['templateDocxPath'] = r.docxPath;
        this.tdocForm['templatePdfPath']  = r.pdfPath;
        this.templateFileName.set(r.fileName);
        this.toast.success('Gabarit chargé');
      },
      error: e => this.toast.error(e?.error?.error ?? 'Chargement du gabarit échoué'),
      complete: () => {
        this.uploadingTemplate.set(false);
        input.value = '';
      },
    });
  }

  async openTdocPreview() {
    this.showTdocPreview.set(true);
    this.previewing.set(true);
    this.previewHtml.set(null);
    // Laisser Angular rendre la modal
    await new Promise<void>(r => setTimeout(r));
    try {
      const raw = this.tdocForm['templateHtml'] || '<p>(aucun contenu)</p>';
      // Convertir toute <img> non-data: en data: URI inline. Plus aucune URL externe
      // n'est passée au backend → pas de SSRF possible par openhtmltopdf.
      const parser = new DOMParser();
      const doc = parser.parseFromString(raw, 'text/html');
      const imgs = Array.from(doc.querySelectorAll('img')) as HTMLImageElement[];
      for (const img of imgs) {
        const src = img.getAttribute('src') || '';
        if (!src || src.startsWith('data:')) continue;
        try {
          const resp = await fetch(src, { credentials: 'same-origin' });
          const blob = await resp.blob();
          const dataUrl = await this.blobToDataUrl(blob);
          img.setAttribute('src', dataUrl);
        } catch {
          // si l'image n'est pas accessible, on la laisse — le backend strippera son src non-data
        }
      }
      const fragment = doc.body.innerHTML;
      this.processedTemplateHtml.set(fragment);  // cache pour download PDF
      this.api.previewHtml(fragment).subscribe({
        next: r => {
          // bypassSecurityTrustHtml empeche DomSanitizer de supprimer le <style> embarque
          // qui rendrait l'iframe blanche. Le HTML vient de notre propre backend authentifie.
          this.previewHtml.set(this.sanitizer.bypassSecurityTrustHtml(r.html));
          this.cdr.detectChanges();
        },
        error: e => this.toast.error('Aperçu impossible : ' + (e?.error?.error ?? e?.message ?? 'erreur')),
        complete: () => this.previewing.set(false),
      });
    } catch (e: any) {
      this.toast.error('Aperçu impossible : ' + (e?.message ?? 'erreur inconnue'));
      this.previewing.set(false);
    }
  }

  closeTdocPreview() {
    this.resizeObserver?.disconnect();
    this.resizeObserver = undefined;
    this.previewHtml.set(null);
    this.processedTemplateHtml.set('');
    this.showTdocPreview.set(false);
  }

  downloadTdocPdf() {
    const html = this.processedTemplateHtml();
    if (!html) return;
    this.downloadingPdf.set(true);
    this.api.generatePdfBlob(html).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const safeName = (this.tdocForm['libelle'] || this.tdocForm['code'] || 'gabarit')
          .toString()
          .replace(/[^a-zA-Z0-9-_]/g, '_');
        a.download = `${safeName}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: e => this.toast.error('Téléchargement impossible : ' + (e?.message ?? 'erreur')),
      complete: () => this.downloadingPdf.set(false),
    });
  }

  private blobToDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const fr = new FileReader();
      fr.onload = () => resolve(fr.result as string);
      fr.onerror = () => reject(fr.error);
      fr.readAsDataURL(blob);
    });
  }

  onPreviewIframeLoad(iframe: HTMLIFrameElement) {
    const doc = iframe.contentDocument;
    if (!doc) return;
    // Attendre le rendu (images data: URI, fonts), puis ajuster la hauteur
    // pour avoir un seul scroll au niveau de la modal.
    requestAnimationFrame(() => {
      const h = doc.documentElement.scrollHeight;
      if (h > 0) iframe.style.height = h + 'px';
    });
  }

  private observePreviewResize() {
    const target = this.previewTarget?.nativeElement;
    const parent = target?.parentElement;
    if (!target || !parent) return;
    this.resizeObserver?.disconnect();
    this.resizeObserver = new ResizeObserver(() => this.updatePreviewScale());
    this.resizeObserver.observe(parent);
    this.updatePreviewScale();
  }

  private updatePreviewScale() {
    const target = this.previewTarget?.nativeElement;
    const parent = target?.parentElement;
    if (!target || !parent) return;
    // -24 px de safety pour scrollbar éventuelle + padding cellule
    const avail = parent.clientWidth - 24;
    const scale = Math.min(1, avail / ParametresComponent.A4_WIDTH_PX);
    target.style.setProperty('--preview-scale', String(scale));
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

  generateWorkflow(t: TypeDocParam) {
    if (!confirm(`Générer un workflow par défaut pour "${t.libelle}" ?\nIl sera inactif — vous pourrez le configurer dans le Designer avant de l'activer.`)) return;
    this.api.generateWorkflowFromTypeDoc(t.id).subscribe({
      next: (res: any) => {
        this.typeDocuments.update(list => list.map(x =>
          x.id === t.id
            ? { ...x, workflowDefinitionId: res.workflowDefinitionId, workflowLibelle: res.workflowLibelle }
            : x
        ));
      },
      error: () => alert('Ce type de document possède déjà un workflow associé.'),
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

}
