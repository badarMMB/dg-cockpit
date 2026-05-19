import { Component, OnInit, signal, inject, computed } from '@angular/core';
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
}

@Component({
  selector: 'app-bureau',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bureau.component.html',
})
export class BureauComponent implements OnInit {
  private api = inject(ApiService);
  private auth   = inject(AuthService);
  private router = inject(Router);

  docs      = signal<BureauDoc[]>([]);
  loading   = signal(false);
  uploading = signal(false);

  filterStatut = signal<string>('TOUS');

  filtered = computed(() => {
    const f = this.filterStatut();
    return f === 'TOUS' ? this.docs() : this.docs().filter(d => d.statut === f);
  });

  // Upload modal
  showUploadModal  = signal(false);
  uploadFile: File | null = null;
  uploadTitre      = '';
  uploadType       = 'COURRIER';
  uploadDestinataire = '';

  // Confirm delete
  deletingId = signal<string | null>(null);

  // Confirm soumettre
  soumettreId = signal<string | null>(null);
  submitting  = signal(false);

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
    RETOURNE:  'Renvoyé par le DG',
    SIGNE:     'Signé par le DG',
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
    this.api.uploadBureauDocument(
      this.uploadFile,
      this.uploadType,
      this.uploadTitre || undefined,
      this.uploadDestinataire || undefined
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
    this.soumettreId.set(doc.id);
  }

  doSoumettre() {
    const id = this.soumettreId();
    if (!id) return;
    this.submitting.set(true);
    this.api.soumettreAuParapheur(id).subscribe({
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
