import {
  Component, OnInit, OnDestroy, AfterViewChecked,
  ViewChild, ElementRef,
  inject, signal, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, WorkflowEtat, WorkflowStep } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { AudioRecorderComponent } from '../../shared/audio-recorder/audio-recorder.component';
import { PdfFileViewerComponent } from '../../shared/pdf-file-viewer/pdf-file-viewer.component';
import { WorkflowTimelineComponent } from '../../shared/workflow-timeline/workflow-timeline.component';

// ── Interfaces locales ────────────────────────────────────────────────────────

interface ChatMessage {
  id: string;
  sender: string;
  text: string;
  time: string;
  isSelf: boolean;
  type: 'normal' | 'final' | 'system';
  hasAttachment: boolean;
  attachmentName?: string;
  actionType?: string;
  status?: 'pending' | 'validated' | 'rejected';
  audioUrl?: string;
}

interface Assignee {
  agent: string;
  role: 'action_signature' | 'action_courrier' | 'action_note' | 'avis_simple';
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, AudioRecorderComponent, PdfFileViewerComponent, WorkflowTimelineComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('chatContainer') chatContainer!: ElementRef;

  private api   = inject(ApiService);
  private auth  = inject(AuthService);
  private toast = inject(ToastService);

  // ── État UI général ───────────────────────────────────────────────────────

  mobileView          = signal<'list' | 'chat'>('list');
  threads             = signal<any[]>([]);
  activeThreadId      = signal<string | null>(null);
  messages            = signal<ChatMessage[]>([]);
  newMessage          = signal('');
  showFinalActionMenu = signal(false);
  selectedFile        = signal<File | null>(null);
  uploadingFile       = signal(false);

  private shouldScrollToBottom = false;
  private eventSource: EventSource | null = null;

  // ── Modale nouvelle instruction ───────────────────────────────────────────

  isNewInstructionModalOpen  = signal(false);
  newInstructionTitle        = signal('');
  newInstructionTypeId       = signal('');
  newInstructionUrgence      = signal('NORMAL');
  newInstructionEcheance     = signal('');
  newInstructionConfidential = signal(false);
  newInstructionMessage      = signal('');
  newInstructionAssignees    = signal<Assignee[]>([]);
  isRecordingAudio           = signal(false);
  hasAudioRecord             = signal(false);

  // ── Aperçu document ───────────────────────────────────────────────────────

  previewFileName = signal<string | null>(null);

  // ── Données de référence ──────────────────────────────────────────────────

  instructionTypes = signal<any[]>([]);
  availableAgents  = signal<string[]>([]);

  // ── Nouveau moteur de workflow ────────────────────────────────────────────

  /** État courant du circuit pour le thread sélectionné */
  workflowEtat = signal<WorkflowEtat | null>(null);

  /** Modale de rejet (motif obligatoire) */
  showRejectionModal = signal(false);
  motifRejet         = signal('');

  /** Panneau latéral droit — timeline du circuit */
  showTimelinePanel = signal(false);

  /** Aperçu du circuit dans la modale de création */
  circuitPreview = signal<WorkflowStep[]>([]);

  /** Option de la modale : lancer le circuit immédiatement après création */
  lancerApresCreation = signal(false);

  // ── Computed : utilisateur courant ────────────────────────────────────────

  readonly currentUser = computed(() => this.auth.currentUser());

  // ── Computed : droits basés sur le nouveau modèle Poste ──────────────────

  /** L'utilisateur est l'acteur requis pour valider/rejeter l'étape courante */
  readonly peutAgirSurEtapeActuelle = computed(() => {
    const etat = this.workflowEtat();
    const user = this.currentUser();
    if (!etat || !user || etat.globalStatus !== 'EN_CIRCUIT') return false;
    const etape = etat.currentStep;
    if (!etape) return false;
    if (!etape.requiredPoste) return true; // étape ouverte à tous
    return !!user.posteId && user.posteId === etape.requiredPoste.id;
  });

  /** Le circuit est actif (EN_CIRCUIT) */
  readonly workflowEnCircuit = computed(() =>
    this.workflowEtat()?.globalStatus === 'EN_CIRCUIT'
  );

  /** L'utilisateur peut lancer le circuit (dossier en brouillon + rôle créateur) */
  readonly peutLancerCircuit = computed(() => {
    const etat = this.workflowEtat();
    const user = this.currentUser();
    if (!etat || !user) return false;
    if (etat.globalStatus !== 'BROUILLON') return false;
    // DG (legacy) ou utilisateur avec poste (nouveau modèle) peuvent lancer
    return user.role === 'DG' || !!user.posteId;
  });

  // ── Computed : droits basés sur le rôle legacy (compatibilité transition) ─

  /** Peut créer une nouvelle instruction (DG/Secrétaire legacy, ou utilisateur avec Poste du nouveau modèle) */
  readonly peutCreerInstruction = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    if (user.role === 'SUBORDONNE') return false;
    return true;
  });

  /** Peut envoyer une Réponse Définitive (rôle subordonné legacy) */
  readonly peutEnvoyerRepDefinitive = computed(() =>
    this.currentUser()?.role === 'SUBORDONNE'
  );

  /** Peut envoyer une clôture (rôle secrétaire legacy) */
  readonly peutEnvoyerCloture = computed(() =>
    this.currentUser()?.role === 'SECRETAIRE'
  );

  /** Peut valider/refuser les messages FINAL (ancien workflow — DG uniquement) */
  readonly peutValiderMessages = computed(() =>
    this.currentUser()?.role === 'DG'
  );

  /** Documents attendus sur le thread actif */
  readonly activeThreadDocuments = computed(() =>
    (this.activeThread()?.documentsAttendus as string[]) ?? []
  );

  /** Documents attendus pour le type sélectionné dans la modale */
  readonly selectedTypeDocuments = computed(() => {
    const typeId = this.newInstructionTypeId();
    const type   = this.instructionTypes().find(t => t.id === typeId);
    return (type?.documentsAttendus as string[]) ?? [];
  });

  // ── Maps de présentation ──────────────────────────────────────────────────

  readonly statutColors: Record<string, string> = {
    // Ancien modèle
    'OUVERT':            'bg-gray-100 text-gray-700',
    'EN_COURS':          'bg-yellow-100 text-yellow-700',
    'EN_ATTENTE':        'bg-orange-100 text-orange-700',
    'SOUMIS_VALIDATION': 'bg-blue-100 text-blue-700',
    'CLOTURE':           'bg-green-100 text-green-700',
    'REFUSE':            'bg-red-100 text-red-700',
    // Nouveau modèle
    'BROUILLON':      'bg-gray-100 text-gray-600',
    'EN_CIRCUIT':     'bg-indigo-100 text-indigo-700',
    'CLOTURE_VALIDE': 'bg-green-100 text-green-800',
    'CLOTURE_REJETE': 'bg-red-100 text-red-800',
  };

  readonly statutLabels: Record<string, string> = {
    // Ancien modèle
    'OUVERT':            'Ouvert',
    'EN_COURS':          'En cours',
    'EN_ATTENTE':        'En attente',
    'SOUMIS_VALIDATION': 'En validation',
    'CLOTURE':           'Clôturé',
    'REFUSE':            'Refusé',
    // Nouveau modèle
    'BROUILLON':      'Brouillon',
    'EN_CIRCUIT':     'En circuit',
    'CLOTURE_VALIDE': 'Clôturé ✓',
    'CLOTURE_REJETE': 'Rejeté',
  };

  readonly urgenceConfig: Record<string, { label: string; cls: string }> = {
    URGENT:   { label: 'Urgent',   cls: 'bg-red-100 text-red-800' },
    NORMAL:   { label: 'Normal',   cls: 'bg-gray-100 text-gray-700' },
    PLANIFIE: { label: 'Planifié', cls: 'bg-blue-100 text-blue-700' },
  };

  readonly availableRoles = [
    { value: 'action_signature', label: 'Action : Document pour signature' },
    { value: 'action_courrier',  label: 'Action : Dépôt Courrier Arrivé' },
    { value: 'action_note',      label: 'Action : Rédiger Note de service' },
    { value: 'avis_simple',      label: 'Avis : Émettre un avis (Consultatif)' },
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    this.chargerThreads();
    this.api.getInstructionTypes(true).subscribe(types => {
      this.instructionTypes.set(types);
      if (types.length > 0) this.newInstructionTypeId.set(types[0].id);
    });
    this.api.getUsers().subscribe(list => {
      // Inclure les utilisateurs SUBORDONNE (legacy) et ceux sans rôle (nouveau modèle)
      this.availableAgents.set(
        list
          .filter((u: any) => u.role === 'SUBORDONNE' || u.role == null)
          .map((u: any) => u.nomComplet as string)
          .filter(Boolean)
      );
    });
    this.connecterSse();
  }

  ngOnDestroy() {
    this.eventSource?.close();
  }

  // ── SSE ───────────────────────────────────────────────────────────────────

  private connecterSse() {
    this.eventSource = new EventSource('/api/events');

    this.eventSource.addEventListener('INSTRUCTION_CREATED', () => {
      this.chargerThreads();
    });

    this.eventSource.addEventListener('INSTRUCTION_UPDATED', (event: MessageEvent) => {
      const data = JSON.parse(event.data);
      // Mettre à jour le statut dans la liste
      this.threads.update(ts => ts.map(t =>
        t.id === data.id
          ? {
              ...t,
              statut:       data.statut       ?? t.statut,
              globalStatus: data.globalStatus ?? t.globalStatus,
            }
          : t
      ));
      // Si c'est le thread actif, rafraîchir messages + état workflow
      if (this.activeThreadId() === data.id) {
        this.rafraichirMessages();
        this.api.getEtatWorkflow(data.id).subscribe({
          next:  etat => this.workflowEtat.set(etat),
          error: ()   => {}
        });
      }
    });
  }

  // ── Chargement des données ────────────────────────────────────────────────

  private chargerThreads() {
    this.api.getInstructions().subscribe(list => {
      const threads = list.map((t: any, i: number) => ({ ...t, active: i === 0 }));
      this.threads.set(threads);
      if (threads.length > 0) this.chargerMessages(threads[0].id);
    });
  }

  private chargerMessages(threadId: string) {
    this.activeThreadId.set(threadId);
    this.rafraichirMessages();
    // Charger l'état du circuit pour ce thread
    this.api.getEtatWorkflow(threadId).subscribe({
      next:  etat => this.workflowEtat.set(etat),
      error: ()   => this.workflowEtat.set(null)
    });
  }

  private rafraichirMessages() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.getMessages(id).subscribe(msgs => {
      this.messages.set(msgs.map((m: any) => this.toMessage(m)));
      this.shouldScrollToBottom = true;
    });
  }

  selectThread(id: string) {
    this.threads.update(t => t.map(th => ({ ...th, active: th.id === id })));
    this.chargerMessages(id);
    this.mobileView.set('chat');
  }

  // ── Helpers de présentation ───────────────────────────────────────────────

  activeThread() { return this.threads().find(t => t.id === this.activeThreadId()); }

  /** Badge d'état : priorité au globalStatus si le circuit est actif/clôturé */
  getBadgeLabel(thread: any): string {
    const gs = thread.globalStatus as string | undefined;
    if (gs === 'EN_CIRCUIT' || gs === 'CLOTURE_VALIDE' || gs === 'CLOTURE_REJETE') {
      return this.statutLabels[gs] ?? gs;
    }
    return this.statutLabels[thread.statut] ?? thread.statut ?? '';
  }

  getBadgeClasses(thread: any): string {
    const gs = thread.globalStatus as string | undefined;
    if (gs === 'EN_CIRCUIT' || gs === 'CLOTURE_VALIDE' || gs === 'CLOTURE_REJETE') {
      return this.statutColors[gs] ?? 'bg-gray-100 text-gray-600';
    }
    return this.statutColors[thread.statut] ?? 'bg-gray-100 text-gray-600';
  }

  urgenceLabel(u: string): string { return this.urgenceConfig[u]?.label ?? u; }
  urgenceCls(u: string):   string { return this.urgenceConfig[u]?.cls   ?? 'bg-gray-100 text-gray-600'; }

  formatEcheance(dateStr: string | null): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  isOverdue(dateStr: string | null): boolean {
    if (!dateStr) return false;
    return new Date(dateStr) < new Date();
  }

  // ── Transformation des messages ───────────────────────────────────────────

  private toMessage(m: any): ChatMessage {
    // Rétro-compatibilité : perspective inversée pour le subordonné legacy
    const isSub = this.currentUser()?.role === 'SUBORDONNE';
    const isSelf = isSub ? !m.isSelf : m.isSelf;
    return {
      id:             m.id,
      sender:         m.sender,
      text:           m.text ?? '',
      time:           m.time,
      isSelf,
      type:           m.type as 'normal' | 'final' | 'system',
      hasAttachment:  m.hasAttachment,
      attachmentName: m.attachmentName,
      actionType:     m.actionType,
      status:         m.status as any,
      audioUrl:       m.audioUrl,
    };
  }

  // ── Modale nouvelle instruction ───────────────────────────────────────────

  openNewInstructionModal() {
    const types     = this.instructionTypes();
    const firstType = types[0];
    this.newInstructionTitle.set('');
    this.newInstructionTypeId.set(firstType?.id ?? '');
    this.newInstructionUrgence.set(firstType?.urgenceDefaut ?? 'NORMAL');
    this.newInstructionEcheance.set('');
    this.newInstructionConfidential.set(false);
    this.newInstructionMessage.set('');
    this.newInstructionAssignees.set([]);
    this.isRecordingAudio.set(false);
    this.hasAudioRecord.set(false);
    this.lancerApresCreation.set(false);
    this.circuitPreview.set([]);
    if (firstType?.id) this.chargerCircuitPreview(firstType.id);
    this.isNewInstructionModalOpen.set(true);
  }

  onTypeSelected(typeId: string) {
    this.newInstructionTypeId.set(typeId);
    const itype = this.instructionTypes().find(t => t.id === typeId);
    if (itype) this.newInstructionUrgence.set(itype.urgenceDefaut);
    this.chargerCircuitPreview(typeId);
  }

  private chargerCircuitPreview(typeId: string) {
    this.circuitPreview.set([]);
    this.api.getWorkflowSteps(typeId).subscribe({
      next:  steps => this.circuitPreview.set([...steps].sort((a, b) => a.stepOrder - b.stepOrder)),
      error: ()    => this.circuitPreview.set([]),
    });
  }

  addAssignee() {
    const agents = this.availableAgents();
    this.newInstructionAssignees.update(list => [
      ...list, { agent: agents[0] ?? '', role: 'avis_simple' }
    ]);
  }

  removeAssignee(index: number) {
    this.newInstructionAssignees.update(list => list.filter((_, i) => i !== index));
  }

  updateAssigneeAgent(index: number, v: string) {
    this.newInstructionAssignees.update(list => {
      const next = [...list]; next[index] = { ...next[index], agent: v }; return next;
    });
  }

  updateAssigneeRole(index: number, v: any) {
    this.newInstructionAssignees.update(list => {
      const next = [...list]; next[index] = { ...next[index], role: v }; return next;
    });
  }

  closeNewInstructionModal() {
    this.isNewInstructionModalOpen.set(false);
    this.circuitPreview.set([]);
    this.lancerApresCreation.set(false);
  }

  createNewInstruction() {
    if (!this.newInstructionMessage().trim() && !this.hasAudioRecord()) return;
    const lancer = this.lancerApresCreation();
    const payload = {
      title:             this.newInstructionTitle().trim() || '',
      instructionTypeId: this.newInstructionTypeId(),
      urgence:           this.newInstructionUrgence(),
      echeance:          this.newInstructionEcheance() || null,
      confidentialite:   this.newInstructionConfidential(),
      message:           this.newInstructionMessage(),
      assignees:         [],
      hasAudio:          this.hasAudioRecord(),
    };
    this.api.createInstruction(payload).subscribe({
      next: created => {
        const newThread = { ...created, active: true, unread: 0 };
        this.threads.update(t => [newThread, ...t.map(th => ({ ...th, active: false }))]);
        this.chargerMessages(created.id);
        this.closeNewInstructionModal();
        if (lancer) this.lancerCircuitPourInstruction(created.id);
      },
      error: err => this.toast.error(err?.error?.error ?? "Impossible de créer l'instruction."),
    });
  }

  // ── Envoi de messages (ancien modèle) ────────────────────────────────────

  toggleActionMenu() { this.showFinalActionMenu.set(!this.showFinalActionMenu()); }

  sendNormalMessage() {
    if (!this.newMessage().trim()) return;
    this.posterMessage('normal');
  }

  sendFinalAction(actionType: string) {
    if (!this.newMessage().trim()) return;
    this.posterMessage('final', actionType);
    this.showFinalActionMenu.set(false);
  }

  private posterMessage(type: 'normal' | 'final', actionType?: string, attachmentName?: string) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const user   = this.currentUser();
    const isSub  = user?.role === 'SUBORDONNE';
    const sender = user?.nomComplet || (isSub ? 'Agent' : 'Inconnu');
    const isSelf = !isSub;

    const payload: any = {
      sender,
      isSelf,
      text:       this.newMessage(),
      type:       type.toUpperCase(),
      actionType: actionType?.toUpperCase(),
    };
    if (attachmentName) {
      payload.attachmentName = attachmentName;
      payload.hasAttachment  = true;
    }

    this.api.sendMessage(threadId, payload).subscribe(saved => {
      this.messages.update(msgs => [...msgs, this.toMessage(saved)]);
      this.newMessage.set('');
      this.shouldScrollToBottom = true;
    });
  }

  onFileSelected(ev: Event) {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (file) this.selectedFile.set(file);
  }

  removeSelectedFile() { this.selectedFile.set(null); }

  sendSubordonneFinal() {
    const file = this.selectedFile();
    if (!this.newMessage().trim() && !file) return;
    if (file) {
      this.uploadingFile.set(true);
      this.api.uploadFile(file).subscribe(res => {
        this.uploadingFile.set(false);
        this.posterMessage('final', undefined, res.name);
        this.removeSelectedFile();
        this.soumettreAutomatique();
      });
    } else {
      this.posterMessage('final');
      this.soumettreAutomatique();
    }
  }

  private soumettreAutomatique() {
    const id = this.activeThreadId();
    if (!id || this.currentUser()?.role !== 'SUBORDONNE') return;
    this.api.soumettre(id, { validateur: 'DG', commentaire: '' }).subscribe(() => {
      this.threads.update(t => t.map(th =>
        th.id === id ? { ...th, statut: 'SOUMIS_VALIDATION' } : th
      ));
    });
  }

  validateFinalAction(msgId: string) {
    this.api.validateMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m =>
        m.id === msgId ? { ...m, status: 'validated' as const } : m
      ));
    });
  }

  rejectFinalAction(msgId: string) {
    this.api.rejectMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m =>
        m.id === msgId ? { ...m, status: 'rejected' as const } : m
      ));
    });
  }

  // ── Actions du moteur de workflow ─────────────────────────────────────────

  private lancerCircuitPourInstruction(id: string) {
    this.api.lancerCircuit(id).subscribe({
      next: etat => {
        this.workflowEtat.set(etat);
        this.threads.update(ts => ts.map(t =>
          t.id === id ? { ...t, globalStatus: etat.globalStatus } : t
        ));
        this.showTimelinePanel.set(true);
        this.toast.success('Circuit lancé — ' + (etat.currentStep?.stepLabel ?? ''));
      },
      error: err => this.toast.error(
        err?.error?.error ?? "Instruction créée, mais le circuit n'a pas pu démarrer."
      ),
    });
  }

  lancerCircuit() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.lancerCircuit(id).subscribe({
      next: etat => {
        this.workflowEtat.set(etat);
        this.threads.update(ts => ts.map(t =>
          t.id === id ? { ...t, globalStatus: etat.globalStatus } : t
        ));
        this.rafraichirMessages();
        this.toast.success('Circuit lancé — ' + (etat.currentStep?.stepLabel ?? ''));
      },
      error: err => this.toast.error(err?.error?.error ?? 'Impossible de lancer le circuit.'),
    });
  }

  validerEtapeWorkflow() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.validerEtape(id).subscribe({
      next: etat => {
        this.workflowEtat.set(etat);
        this.threads.update(ts => ts.map(t =>
          t.id === id ? { ...t, globalStatus: etat.globalStatus } : t
        ));
        this.rafraichirMessages();
        const msg = etat.globalStatus === 'CLOTURE_VALIDE'
          ? 'Dossier clôturé positivement ✅'
          : 'Étape validée — ' + (etat.currentStep?.stepLabel ?? 'étape suivante');
        this.toast.success(msg);
      },
      error: err => this.toast.error(err?.error?.error ?? 'Validation refusée.'),
    });
  }

  ouvrirModalRejet() {
    this.motifRejet.set('');
    this.showRejectionModal.set(true);
  }

  fermerModalRejet() {
    this.showRejectionModal.set(false);
    this.motifRejet.set('');
  }

  rejeterEtapeWorkflow() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.rejeterEtape(id, this.motifRejet()).subscribe({
      next: etat => {
        this.workflowEtat.set(etat);
        this.threads.update(ts => ts.map(t =>
          t.id === id ? { ...t, globalStatus: etat.globalStatus } : t
        ));
        this.fermerModalRejet();
        this.rafraichirMessages();
        this.toast.info('Dossier clôturé — étape rejetée.');
      },
      error: err => this.toast.error(err?.error?.error ?? 'Rejet impossible.'),
    });
  }

  // ── Audio ─────────────────────────────────────────────────────────────────

  onAudioRecorded(blob: Blob) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const file = new File([blob], `voice_${Date.now()}.webm`, { type: 'audio/webm' });
    this.api.uploadFile(file).subscribe(res => {
      const user    = this.currentUser();
      const payload = {
        sender: user?.nomComplet || 'DG',
        isSelf: true,
        text:   '',
        type:   'NORMAL',
        attachmentName: res.name,
        audioUrl: this.api.getFileUrl(res.name),
      };
      this.api.sendMessage(threadId, payload).subscribe(saved => {
        const msg = this.toMessage(saved);
        if (!msg.audioUrl) msg.audioUrl = payload.audioUrl;
        this.messages.update(msgs => [...msgs, msg]);
        this.shouldScrollToBottom = true;
      });
    });
  }

  toggleAudioRecording() {
    this.isRecordingAudio.update(v => !v);
    if (!this.isRecordingAudio()) this.hasAudioRecord.set(true);
  }

  // ── Keyboard / scroll ─────────────────────────────────────────────────────

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendNormalMessage();
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      try {
        this.chatContainer.nativeElement.scrollTop =
          this.chatContainer.nativeElement.scrollHeight;
      } catch (_) {}
      this.shouldScrollToBottom = false;
    }
  }

  // ── Aperçu PDF ────────────────────────────────────────────────────────────

  openPreview(attachmentName: string)  { this.previewFileName.set(attachmentName); }
  closePreview()                        { this.previewFileName.set(null); }
}
