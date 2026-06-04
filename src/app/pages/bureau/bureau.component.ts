import { Component, OnInit, OnDestroy, signal, inject, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { take } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { CollaboraEditorComponent } from '../../shared/collabora-editor/collabora-editor.component';

interface BureauDoc {
  id: string;
  titre: string;
  type: string;
  destinataire: string | null;
  originalFileName: string;
  pageCount: number;
  statut: 'BROUILLON' | 'SOUMIS' | 'RETOURNE' | 'SIGNE' | 'LIVRE';
  pdfDocumentId: string | null;
  createdAt: string;
  hasSignatureZone: boolean;
  hasStampZone: boolean;
  hasSignaturePdf: boolean;
  renvoyeMotif: string | null;
  corrigeDepuisRenvoi: boolean;
  circuitPdfDocumentId: string | null;
  typeDocumentId: string | null;
  modeCircuit: 'MANAGER_SEUL' | 'LIBRE' | 'PREDEFINI' | 'PREDEFINI_MODIFIABLE' | null;
  typeDocCircuit: { posteId: string; posteLibelle: string }[] | null;
}

interface TypeDoc {
  id: string;
  code: string;
  libelle: string;
  modeCircuit: 'MANAGER_SEUL' | 'LIBRE' | 'PREDEFINI' | 'PREDEFINI_MODIFIABLE';
  actionFinale: 'ARCHIVER' | 'PUBLIER';
  circuit: { posteId: string; posteLibelle: string }[];
  initiateurPostes: string[];
  requiresSignatureZone: boolean;
  requiresStampZone: boolean;
  requiresDestinataire: boolean;
  actif: boolean;
  templateDocxPath?: string | null;
}

interface EtapeCircuit {
  userId: string;
  nom: string;
}

@Component({
  selector: 'app-bureau',
  standalone: true,
  imports: [CommonModule, FormsModule, CollaboraEditorComponent],
  templateUrl: './bureau.component.html',
})
export class BureauComponent implements OnInit, OnDestroy {
  private api   = inject(ApiService);
  private auth  = inject(AuthService);
  private router = inject(Router);
  private route  = inject(ActivatedRoute);

  private sse: EventSource | null = null;

  currentUser = this.auth.currentUser;

  docs      = signal<BureauDoc[]>([]);
  loading   = signal(false);
  uploading = signal(false);

  filterStatut = signal<string>('TOUS');

  filtered = computed(() => {
    const f = this.filterStatut();
    return f === 'TOUS' ? this.docs() : this.docs().filter(d => d.statut === f);
  });

  // Upload modal
  showUploadModal    = signal(false);
  uploadFile: File | null = null;
  uploadTitre        = '';
  uploadType         = 'COURRIER';
  uploadDestinataire = '';
  uploadTypeDocumentId = signal<string>('');

  // Liste des types de documents disponibles
  typeDocuments = signal<TypeDoc[]>([]);
  // TypeDocument sélectionné pour l'upload en cours
  uploadTypeDoc = computed(() =>
    this.typeDocuments().find(t => t.id === this.uploadTypeDocumentId()) ?? null
  );

  // Section Instruction dans la modale d'upload
  uploadInstructionMode          = signal<'AUCUNE' | 'EXISTANTE'>('AUCUNE');
  uploadInstructionId            = signal<string>('');
  instructionsPendingForTypeDoc  = signal<{ id: string; title: string; statut: string }[]>([]);
  loadingInstructionsPending     = signal(false);

  // Création depuis template
  creatingFromTemplate = signal(false);

  creerDepuisModele() {
    const td = this.uploadTypeDoc();
    if (!td) return;
    this.creatingFromTemplate.set(true);
    const instrId = this.uploadInstructionMode() === 'EXISTANTE'
      ? (this.uploadInstructionId() || undefined)
      : undefined;
    this.api.createFromTemplate(td.id, this.uploadTitre || undefined, instrId).subscribe({
      next: (doc: any) => {
        this.docs.update(list => [{ ...doc, hasSignatureZone: false, hasStampZone: false } as any, ...list]);
        this.showUploadModal.set(false);
        this.creatingFromTemplate.set(false);
        // Ouvrir immédiatement Collabora sur le nouveau document
        this.editingDoc.set(doc as any);
      },
      error: () => this.creatingFromTemplate.set(false),
    });
  }

  // Éditeur Collabora — document ouvert en plein écran
  editingDoc = signal<BureauDoc | null>(null);

  ouvrirEdition(doc: BureauDoc) {
    if (doc.statut === 'BROUILLON' || doc.statut === 'RETOURNE') {
      this.editingDoc.set(doc);
    }
  }

  fermerEdition() {
    this.editingDoc.set(null);
    this.load(); // rafraîchit la liste après modification
  }

  // Confirm delete
  deletingId = signal<string | null>(null);

  // Modale soumettre avec sélection de circuit
  /** Retourne true si le document est un .docx éditable via Collabora. */
  isDocx(doc: BureauDoc): boolean {
    return !!doc.originalFileName?.toLowerCase().endsWith('.docx');
  }

  /**
   * Soumission autorisée une fois la zone de signature posée (PDF comme .docx).
   * Pour un .docx, les zones sont posées sur le PDF régénéré à l'enregistrement Collabora.
   */
  peutSoumettre(doc: BureauDoc): boolean {
    if (doc.statut === 'RETOURNE') return doc.hasSignatureZone && doc.corrigeDepuisRenvoi;
    return doc.hasSignatureZone;
  }

  /** Le placement de zones est possible une fois le PDF disponible (PDF natif ou .docx déjà enregistré dans Collabora). */
  peutPlacerZones(doc: BureauDoc): boolean {
    return !this.isDocx(doc) || doc.hasSignaturePdf;
  }

  soumettreId        = signal<string | null>(null);
  submitting         = signal(false);
  circuitSelectionne = signal<EtapeCircuit[]>([]);

  soumettreDoc = computed(() => {
    const id = this.soumettreId();
    return id ? (this.docs().find(d => d.id === id) ?? null) : null;
  });

  // Sélecteur d'utilisateurs pour ajouter une étape au circuit
  utilisateurs      = signal<{ id: string; nomComplet: string; posteId?: string }[]>([]);
  showUserPicker    = signal(false);
  userPickerFilter  = signal('');

  utilisateursFiltres = computed(() => {
    const filtre      = this.userPickerFilter().toLowerCase();
    const dejaChoisis = new Set(this.circuitSelectionne().map(e => e.userId));
    const moiId       = this.currentUser()?.id;
    return this.utilisateurs()
      .filter(u => !dejaChoisis.has(u.id) && u.id !== moiId)
      .filter(u => !filtre || u.nomComplet.toLowerCase().includes(filtre));
  });

  readonly docTypes = [
    { value: 'COURRIER',      label: 'Courrier officiel' },
    { value: 'NOTE_SERVICE',  label: 'Note de service' },
    { value: 'DECISION',      label: 'Décision' },
    { value: 'TRANSMISSION',  label: 'Lettre de transmission' },
    { value: 'INVITATION',    label: 'Invitation' },
    { value: 'VOEUX',         label: 'Vœux' },
    { value: 'AUTRE',         label: 'Autre' },
  ];

  readonly statutCls: Record<string, string> = {
    BROUILLON: 'bg-amber-100 text-amber-700',
    SOUMIS:    'bg-blue-100 text-blue-700',
    RETOURNE:  'bg-orange-100 text-orange-700',
    SIGNE:     'bg-green-100 text-green-700',
    LIVRE:     'bg-emerald-100 text-emerald-700',
  };
  readonly statutLabel: Record<string, string> = {
    BROUILLON: 'Brouillon',
    SOUMIS:    'Soumis au parapheur',
    RETOURNE:  'Renvoyé pour correction',
    SIGNE:     'Signé',
    LIVRE:     'Livré & Classé',
  };

  // Modale livraison bureau
  showLivraisonModal  = signal(false);
  livraisonDocId      = signal<string>('');
  livraisonDocTitre   = signal<string>('');
  livraisonScan       = signal<File | null>(null);
  livraisonClasseurs  = signal<any[]>([]);
  livraisonSelected   = signal<string[]>([]);
  livraisonSaving     = signal(false);

  constructor() {
    // Quand le TypeDocument change et qu'il a un binôme, charger les instructions en attente
    // allowSignalWrites: true requis pour écrire dans des signaux depuis l'effect
    effect(() => {
      const tdId = this.uploadTypeDocumentId();
      if (tdId) {
        this.loadingInstructionsPending.set(true);
        this.api.getInstructionsPendingForTypeDoc(tdId).subscribe({
          next: list => {
            this.instructionsPendingForTypeDoc.set(list);
            this.loadingInstructionsPending.set(false);
          },
          error: () => {
            this.instructionsPendingForTypeDoc.set([]);
            this.loadingInstructionsPending.set(false);
          },
        });
      } else {
        this.instructionsPendingForTypeDoc.set([]);
        this.uploadInstructionMode.set('AUCUNE');
        this.uploadInstructionId.set('');
      }
    }, { allowSignalWrites: true });
  }

  ngOnInit() {
    this.load();
    this.api.getClasseurs().subscribe(data => this.livraisonClasseurs.set(data));
    this.api.getUsers().subscribe((users: any[]) =>
      this.utilisateurs.set(users.map(u => ({ id: u.id, nomComplet: u.nomComplet, posteId: u.posteId ?? undefined })))
    );
    this.api.getTypeDocuments().subscribe((types: TypeDoc[]) =>
      this.typeDocuments.set(types.filter(t => t.actif))
    );
    // Ouvrir automatiquement la modale d'upload si on arrive depuis le chat (instruction DOCUMENTAIRE)
    this.route.queryParams.pipe(take(1)).subscribe(params => {
      const typeDocId        = params['typeDocumentId'];
      const srcInstructionId = params['sourceInstructionId'];
      if (typeDocId) {
        this.uploadTypeDocumentId.set(typeDocId);
        this.uploadTitre          = '';
        this.uploadFile           = null;
        this.uploadDestinataire   = '';
        if (srcInstructionId) {
          this.uploadInstructionMode.set('EXISTANTE');
          this.uploadInstructionId.set(srcInstructionId);
        } else {
          this.uploadInstructionMode.set('AUCUNE');
          this.uploadInstructionId.set('');
        }
        this.showUploadModal.set(true);
      }
    });
    this.sse = new EventSource('/api/events');
    this.sse.addEventListener('PARAPHEUR_UPDATED', () => this.load());
  }

  ngOnDestroy() {
    this.sse?.close();
  }

  load() {
    this.loading.set(true);
    this.api.getBureauDocuments().subscribe({
      next: list => { this.docs.set(list); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  // ── Upload ────────────────────────────────────────────────────────────────

  openUploadModal() {
    this.uploadFile           = null;
    this.uploadTitre          = '';
    this.uploadType           = 'COURRIER';
    this.uploadDestinataire   = '';
    this.uploadTypeDocumentId.set('');
    this.uploadInstructionMode.set('AUCUNE');
    this.uploadInstructionId.set('');
    this.showUploadModal.set(true);
  }

  onFileSelected(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (!f) return;
    this.uploadFile = f;
    if (!this.uploadTitre) {
      this.uploadTitre = f.name.replace(/\.(pdf|docx)$/i, '');
    }
  }

  doUpload() {
    if (!this.uploadFile) return;
    this.uploading.set(true);
    const td   = this.uploadTypeDoc();
    const type = td ? td.code : this.uploadType;
    const mode = this.uploadInstructionMode();
    // AUCUNE → le backend auto-crée l'instruction via InstructionType.typeDocumentAttenduId
    // EXISTANTE → on passe l'ID de l'instruction sélectionnée
    const instructionId = mode === 'EXISTANTE' ? (this.uploadInstructionId() || undefined) : undefined;
    this.effectuerUpload(type, instructionId);
  }

  private effectuerUpload(type: string, sourceInstructionId?: string) {
    this.api.uploadBureauDocument(
      this.uploadFile!,
      type,
      this.uploadTitre || undefined,
      this.uploadDestinataire || undefined,
      this.uploadTypeDocumentId() || undefined,
      sourceInstructionId
    ).subscribe({
      next: doc => {
        this.docs.update(list => [doc, ...list]);
        this.uploading.set(false);
        this.showUploadModal.set(false);
      },
      error: () => this.uploading.set(false),
    });
  }

  // ── Placement zones ───────────────────────────────────────────────────────

  openPlacement(doc: BureauDoc) {
    this.router.navigate(['/bureau-placement', doc.id]);
  }

  // ── Soumettre au parapheur ────────────────────────────────────────────────

  confirmSoumettre(doc: BureauDoc) {
    if (!this.peutSoumettre(doc)) {
      if (this.isDocx(doc)) return; // bouton désactivé côté HTML, ne devrait pas arriver
      alert('Veuillez d\'abord placer la zone de signature avant de soumettre.');
      return;
    }
    this.showUserPicker.set(false);
    this.userPickerFilter.set('');

    if (doc.circuitPdfDocumentId) {
      // Document de transit : transmission directe à l'étape suivante
      this.circuitSelectionne.set([]);
      this.soumettreId.set(doc.id);
      return;
    }

    const mode = doc.modeCircuit ?? 'LIBRE';

    if (mode === 'MANAGER_SEUL') {
      const user = this.currentUser();
      const circuit: EtapeCircuit[] = user?.managerId && user?.managerNom
        ? [{ userId: user.managerId, nom: user.managerNom }]
        : [];
      this.circuitSelectionne.set(circuit);
      this.soumettreId.set(doc.id);
      return;
    }

    if (mode === 'PREDEFINI') {
      this.circuitSelectionne.set([]);
      this.soumettreId.set(doc.id);
      return;
    }

    if (mode === 'PREDEFINI_MODIFIABLE' && doc.typeDocCircuit?.length) {
      const filledCircuit: EtapeCircuit[] = [];
      for (const step of doc.typeDocCircuit) {
        const occupant = this.utilisateurs().find(u => u.posteId === step.posteId);
        if (occupant) filledCircuit.push({ userId: occupant.id, nom: occupant.nomComplet });
      }
      this.circuitSelectionne.set(filledCircuit);
      this.soumettreId.set(doc.id);
      return;
    }

    // LIBRE ou fallback : manager par défaut
    const user = this.currentUser();
    const circuit: EtapeCircuit[] = [];
    if (user?.managerId && user?.managerNom) {
      circuit.push({ userId: user.managerId, nom: user.managerNom });
    }
    this.circuitSelectionne.set(circuit);
    this.soumettreId.set(doc.id);
  }

  toggleUserPicker() {
    this.showUserPicker.update(v => !v);
    this.userPickerFilter.set('');
  }

  ajouterSignataire(user: { id: string; nomComplet: string }) {
    this.circuitSelectionne.update(c => [...c, { userId: user.id, nom: user.nomComplet }]);
    this.showUserPicker.set(false);
    this.userPickerFilter.set('');
  }

  supprimerEtape(index: number) {
    const circuit = this.circuitSelectionne();
    if (circuit.length <= 1) return;
    this.circuitSelectionne.set(circuit.filter((_, i) => i !== index));
  }

  monterEtape(index: number) {
    if (index === 0) return;
    const circuit = [...this.circuitSelectionne()];
    [circuit[index - 1], circuit[index]] = [circuit[index], circuit[index - 1]];
    this.circuitSelectionne.set(circuit);
  }

  descendreEtape(index: number) {
    const circuit = this.circuitSelectionne();
    if (index >= circuit.length - 1) return;
    const updated = [...circuit];
    [updated[index], updated[index + 1]] = [updated[index + 1], updated[index]];
    this.circuitSelectionne.set(updated);
  }

  doSoumettre() {
    const id = this.soumettreId();
    if (!id) return;
    const doc = this.soumettreDoc();
    const isTransit   = !!doc?.circuitPdfDocumentId;
    const isPredefini = doc?.modeCircuit === 'PREDEFINI';
    const circuit = this.circuitSelectionne();
    if (!isTransit && !isPredefini && circuit.length === 0) {
      alert('Aucun supérieur hiérarchique défini. Impossible de soumettre.');
      return;
    }
    this.submitting.set(true);
    const circuitParam = (isTransit || isPredefini) ? undefined : JSON.stringify(circuit);
    this.api.soumettreAuParapheur(id, circuitParam).subscribe({
      next: () => {
        this.docs.update(list => list.map(d =>
          d.id === id ? { ...d, statut: 'SOUMIS' as const, renvoyeMotif: null } : d
        ));
        this.submitting.set(false);
        this.soumettreId.set(null);
      },
      error: () => this.submitting.set(false),
    });
  }

  // ── Suppression ───────────────────────────────────────────────────────────

  confirmDelete(id: string) { this.deletingId.set(id); }

  doDelete() {
    const id = this.deletingId();
    if (!id) return;
    this.api.deleteBureauDocument(id).subscribe({
      next: () => {
        this.docs.update(list => list.filter(d => d.id !== id));
        this.deletingId.set(null);
      },
    });
  }

  downloadSigned(doc: BureauDoc) {
    if (!doc.pdfDocumentId) return;
    this.api.downloadFinalPdfBlob(doc.pdfDocumentId, doc.titre + '.pdf');
  }

  // ── Modale livraison ──────────────────────────────────────────────────────

  openLivraison(doc: BureauDoc) {
    this.livraisonDocId.set(doc.id);
    this.livraisonDocTitre.set(doc.titre);
    this.livraisonScan.set(null);
    this.livraisonSelected.set([]);
    this.showLivraisonModal.set(true);
  }

  onLivraisonScan(event: Event) {
    const f = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.livraisonScan.set(f);
  }

  toggleLivraisonClasseur(id: string) {
    const cur = this.livraisonSelected();
    this.livraisonSelected.set(
      cur.includes(id) ? cur.filter(x => x !== id) : [...cur, id]
    );
  }

  confirmerLivraison() {
    const scan = this.livraisonScan();
    if (!scan || this.livraisonSelected().length === 0) return;
    this.livraisonSaving.set(true);
    this.api.livrerBureauDoc(this.livraisonDocId(), scan, this.livraisonSelected())
      .subscribe({
        next: () => {
          this.livraisonSaving.set(false);
          this.showLivraisonModal.set(false);
          this.docs.update(list =>
            list.map(d => d.id === this.livraisonDocId()
              ? { ...d, statut: 'LIVRE' as const }
              : d
            )
          );
        },
        error: () => this.livraisonSaving.set(false),
      });
  }

  typeLabel(v: string): string {
    return this.docTypes.find(t => t.value === v)?.label ?? v;
  }

  formatDate(s: string): string {
    return new Date(s).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
