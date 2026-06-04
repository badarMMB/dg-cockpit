import { Component, inject, signal, computed, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { CollaboraEditorComponent } from '../../shared/collabora-editor/collabora-editor.component';

@Component({
  selector: 'app-signature',
  standalone: true,
  imports: [CommonModule, FormsModule, CollaboraEditorComponent],
  templateUrl: './signature.component.html',
  styleUrl: './signature.component.css'
})
export class SignatureComponent implements OnInit, OnDestroy {
  private api    = inject(ApiService);
  private auth   = inject(AuthService);
  private router = inject(Router);

  activeTab  = signal<'pending' | 'history'>('pending');
  pending    = signal<any[]>([]);
  historique = signal<any[]>([]);
  loading    = signal(false);

  private sse: EventSource | null = null;

  // Submit modal
  showSubmitModal    = signal(false);
  submitFile         = signal<File | null>(null);
  submitTitle        = signal('');
  submitType         = signal<'COURRIER' | 'NOTE_SERVICE'>('COURRIER');
  submitDestinataire = signal('');
  submitting         = signal(false);
  submitError        = signal('');

  // Reject modal
  showRejectModal = signal(false);
  rejectDocId     = signal('');
  rejectComment   = signal('');
  rejecting       = signal(false);

  // Renvoyer modal (EN_ATTENTE_SIGNATURE → renvoi en cascade)
  showRenvoyerModal = signal(false);
  renvoyerDocId     = signal('');
  renvoyerComment   = signal('');
  renvoyant         = signal(false);

  // Renvoyer au propriétaire modal (EN_CORRECTION → renvoyer directement au bureau)
  showRenvoyerProprietaireModal = signal(false);
  renvoyerProprietaireDocId     = signal('');
  renvoyerProprietaireComment   = signal('');
  renvoyantProprietaire         = signal(false);

  currentUser  = computed(() => this.auth.currentUser());
  isSecretaire = computed(() => this.currentUser()?.role === 'SECRETAIRE');
  canSign      = computed(() => this.currentUser()?.canSign === true);

  pendingSignature  = computed(() => this.pending().filter(d => d.parapheurStatut === 'EN_ATTENTE_SIGNATURE'));
  pendingCorrection = computed(() => this.pending().filter(d => d.parapheurStatut === 'EN_CORRECTION'));
  pendingCount      = computed(() => this.pending().length);

  // Éditeur Collabora — document ouvert pour révision/signature
  @ViewChild('collaboraEditor') collaboraEditor?: CollaboraEditorComponent;
  editingParapheurDoc = signal<any | null>(null);
  signing             = signal(false);
  signError           = signal('');

  ouvrirEditionParapheur(doc: any) {
    this.editingParapheurDoc.set(doc);
    this.signError.set('');
  }

  fermerEditionParapheur() {
    this.editingParapheurDoc.set(null);
    this.signing.set(false);
    this.load();
  }

  /**
   * Flux de signature complet pour un document .docx :
   * 1. Récupérer l'image de signature de l'utilisateur courant
   * 2. Injecter via postMessage Collabora → autosave WOPI PutFile
   * 3. Écouter documentSaved → appeler api.signerDocument()
   * 4. Fermer l'éditeur et rafraîchir
   */
  signerDocumentCollabora(doc: any) {
    if (this.signing()) return;
    this.signing.set(true);
    this.signError.set('');

    // Récupérer l'asset signature de l'utilisateur courant
    this.api.getMySignatureAsset().subscribe({
      next: (asset: any) => {
        if (!asset?.base64) {
          // Pas d'image de signature configurée — signer quand même (PDFBox gère les zones)
          this.appellerSigner(doc);
          return;
        }
        const editor = this.collaboraEditor;
        if (!editor) { this.appellerSigner(doc); return; }

        // Injecter la signature graphique dans le .docx via postMessage
        editor.injectSignature('SignZone_DG', asset.base64);

        // Attendre que Collabora confirme la sauvegarde (PutFile WOPI déclenché)
        const sub = editor.documentSaved.subscribe(() => {
          sub.unsubscribe();
          this.appellerSigner(doc);
        });

        // Timeout de sécurité : signer quand même après 5s si pas d'ack
        setTimeout(() => { sub.unsubscribe(); this.appellerSigner(doc); }, 5000);
      },
      error: () => this.appellerSigner(doc)  // pas d'asset → signer quand même
    });
  }

  private appellerSigner(doc: any) {
    this.api.signerDocument(doc.id).subscribe({
      next: () => {
        this.signing.set(false);
        this.editingParapheurDoc.set(null);
        this.load();
      },
      error: (err: any) => {
        this.signing.set(false);
        this.signError.set('Erreur lors de la signature. Veuillez réessayer.');
      }
    });
  }

  ngOnInit() {
    this.load();
    this.sse = new EventSource('/api/events');
    this.sse.addEventListener('PARAPHEUR_UPDATED', () => this.load());
  }

  ngOnDestroy() {
    this.sse?.close();
  }

  load() {
    this.loading.set(true);
    this.api.getParapheurPending().subscribe({
      next: docs => { this.pending.set(docs); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
    this.api.getParapheurHistorique().subscribe({
      next: docs => this.historique.set(docs),
    });
  }

  openDoc(doc: any) {
    this.router.navigate(['/pdf-viewer', doc.id]);
  }

  // ── Submit ───────────────────────────────────────────────────────────────

  openSubmitModal() {
    this.submitFile.set(null);
    this.submitTitle.set('');
    this.submitType.set('COURRIER');
    this.submitDestinataire.set('');
    this.submitError.set('');
    this.showSubmitModal.set(true);
  }

  onFileSelected(ev: Event) {
    const file = (ev.target as HTMLInputElement).files?.[0] ?? null;
    this.submitFile.set(file);
    if (file && !this.submitTitle()) {
      this.submitTitle.set(file.name.replace(/\.pdf$/i, ''));
    }
  }

  submitDocument() {
    const file = this.submitFile();
    if (!file || !this.submitTitle()) return;
    this.submitting.set(true);
    this.submitError.set('');
    this.api.soumettreDocument(file, this.submitTitle(), this.submitType(), this.submitDestinataire())
      .subscribe({
        next: () => { this.showSubmitModal.set(false); this.submitting.set(false); this.load(); },
        error: () => {
          this.submitError.set('Erreur lors de la soumission. Vérifiez le fichier et réessayez.');
          this.submitting.set(false);
        },
      });
  }

  // ── Reject ───────────────────────────────────────────────────────────────

  openRejectModal(doc: any) {
    this.rejectDocId.set(doc.id);
    this.rejectComment.set('');
    this.showRejectModal.set(true);
  }

  confirmReject() {
    if (!this.rejectComment().trim()) return;
    this.rejecting.set(true);
    this.api.rejeterDocument(this.rejectDocId(), this.rejectComment()).subscribe({
      next: () => { this.showRejectModal.set(false); this.rejecting.set(false); this.load(); },
      error: () => this.rejecting.set(false),
    });
  }

  // ── Renvoyer (EN_ATTENTE_SIGNATURE → cascade vers l'étape précédente) ────

  openRenvoyerModal(doc: any) {
    this.renvoyerDocId.set(doc.id);
    this.renvoyerComment.set('');
    this.showRenvoyerModal.set(true);
  }

  confirmRenvoyer() {
    if (!this.renvoyerComment().trim()) return;
    this.renvoyant.set(true);
    this.api.renvoyerDocument(this.renvoyerDocId(), this.renvoyerComment()).subscribe({
      next: () => { this.showRenvoyerModal.set(false); this.renvoyant.set(false); this.load(); },
      error: () => this.renvoyant.set(false),
    });
  }

  // ── Repousser (EN_CORRECTION → re-pousser vers l'étape suivante) ─────────

  repousser(doc: any) {
    this.api.repousserDocument(doc.id).subscribe({
      next: () => this.load(),
    });
  }

  // ── Renvoyer au propriétaire (EN_CORRECTION → renvoyer au bureau source) ──

  openRenvoyerProprietaireModal(doc: any) {
    this.renvoyerProprietaireDocId.set(doc.id);
    this.renvoyerProprietaireComment.set('');
    this.showRenvoyerProprietaireModal.set(true);
  }

  confirmRenvoyerProprietaire() {
    if (!this.renvoyerProprietaireComment().trim()) return;
    this.renvoyantProprietaire.set(true);
    this.api.renvoyerProprietaireDocument(
      this.renvoyerProprietaireDocId(),
      this.renvoyerProprietaireComment()
    ).subscribe({
      next: () => {
        this.showRenvoyerProprietaireModal.set(false);
        this.renvoyantProprietaire.set(false);
        this.load();
      },
      error: () => this.renvoyantProprietaire.set(false),
    });
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  typeLabel(type: string): string {
    return type === 'NOTE_SERVICE' ? 'Note de service' : 'Courrier';
  }

  typeClass(type: string): string {
    return type === 'NOTE_SERVICE'
      ? 'bg-blue-100 text-blue-800'
      : 'bg-purple-100 text-purple-800';
  }

  statutLabel(s: string): string {
    const m: Record<string, string> = {
      SIGNE: 'Signé', REFUSE: 'Refusé', PUBLIE: 'Publié', ARCHIVE: 'Archivé',
      RENVOYE: 'Renvoyé pour correction',
    };
    return m[s] ?? s;
  }

  statutClass(s: string): string {
    switch (s) {
      case 'SIGNE': case 'PUBLIE': return 'bg-green-100 text-green-800';
      case 'REFUSE':               return 'bg-red-100 text-red-800';
      case 'RENVOYE':              return 'bg-amber-100 text-amber-800';
      default:                     return 'bg-gray-100 text-gray-700';
    }
  }

  formatDate(dt: string | null): string {
    if (!dt) return '—';
    return new Date(dt).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }
}
