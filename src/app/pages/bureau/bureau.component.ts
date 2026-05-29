import { Component, OnInit, OnDestroy, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

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
}

interface EtapeCircuit {
  userId: string;
  nom: string;
}

@Component({
  selector: 'app-bureau',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bureau.component.html',
})
export class BureauComponent implements OnInit, OnDestroy {
  private api  = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);

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
  uploadTypeDocumentId = '';

  // Liste des types de documents disponibles
  typeDocuments = signal<TypeDoc[]>([]);
  // TypeDocument sélectionné pour l'upload en cours
  uploadTypeDoc = computed(() =>
    this.typeDocuments().find(t => t.id === this.uploadTypeDocumentId) ?? null
  );

  // Confirm delete
  deletingId = signal<string | null>(null);

  // Modale soumettre avec sélection de circuit
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
    const filtre     = this.userPickerFilter().toLowerCase();
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

  ngOnInit() {
    this.load();
    this.api.getClasseurs().subscribe(data => this.livraisonClasseurs.set(data));
    this.api.getUsers().subscribe((users: any[]) =>
      this.utilisateurs.set(users.map(u => ({ id: u.id, nomComplet: u.nomComplet, posteId: u.posteId ?? undefined })))
    );
    this.api.getTypeDocuments().subscribe((types: TypeDoc[]) =>
      this.typeDocuments.set(types.filter(t => t.actif))
    );
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
    this.uploadFile = null;
    this.uploadTitre = '';
    this.uploadType = 'COURRIER';
    this.uploadDestinataire = '';
    this.uploadTypeDocumentId = '';
    this.showUploadModal.set(true);
  }

  onFileSelected(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (!f) return;
    this.uploadFile = f;
    if (!this.uploadTitre) this.uploadTitre = f.name.replace(/\.pdf$/i, '');
  }

  doUpload() {
    if (!this.uploadFile) return;
    this.uploading.set(true);
    const td = this.uploadTypeDoc();
    const type = td ? td.code : this.uploadType;
    this.api.uploadBureauDocument(
      this.uploadFile,
      type,
      this.uploadTitre || undefined,
      this.uploadDestinataire || undefined,
      this.uploadTypeDocumentId || undefined
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
    if (!doc.hasSignatureZone) {
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
      // Circuit automatique — pas de picker, manager direct uniquement
      const user = this.currentUser();
      const circuit: EtapeCircuit[] = user?.managerId && user?.managerNom
        ? [{ userId: user.managerId, nom: user.managerNom }]
        : [];
      this.circuitSelectionne.set(circuit);
      this.soumettreId.set(doc.id);
      return;
    }

    if (mode === 'PREDEFINI') {
      // Circuit fixé — résolu côté backend, afficher en lecture seule
      this.circuitSelectionne.set([]);
      this.soumettreId.set(doc.id);
      return;
    }

    if (mode === 'PREDEFINI_MODIFIABLE' && doc.typeDocCircuit?.length) {
      // Pré-remplir avec le circuit type (résolution poste → utilisateur), modifiable
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
    if (circuit.length <= 1) return; // garder au moins une étape
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
    const isTransit  = !!doc?.circuitPdfDocumentId;
    const isPredefini = doc?.modeCircuit === 'PREDEFINI';
    const circuit = this.circuitSelectionne();
    if (!isTransit && !isPredefini && circuit.length === 0) {
      alert('Aucun supérieur hiérarchique défini. Impossible de soumettre.');
      return;
    }
    this.submitting.set(true);
    // Pour PREDEFINI : pas de circuit côté frontend — le backend résout via TypeDocument.circuitJson
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
