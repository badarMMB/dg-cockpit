import {
  Component, OnInit, OnDestroy, AfterViewChecked,
  ViewChild, ElementRef,
  inject, signal, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { AudioRecorderComponent } from '../../shared/audio-recorder/audio-recorder.component';
import { PdfFileViewerComponent } from '../../shared/pdf-file-viewer/pdf-file-viewer.component';

// ── Interfaces locales ────────────────────────────────────────────────────────

interface ChatMessage {
  id: string;
  sender: string;
  text: string;
  time: string;
  isSelf: boolean;
  isSystemMessage: boolean;
  hasAttachment: boolean;
  attachmentName?: string;
  audioUrl?: string;
  highlightsJson?: string;
}

interface Assignee {
  agent: string;
  userId?: string;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, AudioRecorderComponent, PdfFileViewerComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('chatContainer') chatContainer!: ElementRef;

  private api    = inject(ApiService);
  private auth   = inject(AuthService);
  private toast  = inject(ToastService);
  private router = inject(Router);

  // ── État UI général ───────────────────────────────────────────────────────

  mobileView     = signal<'list' | 'chat'>('list');
  threads        = signal<any[]>([]);
  activeThreadId = signal<string | null>(null);
  messages       = signal<ChatMessage[]>([]);
  newMessage     = signal('');
  selectedFile   = signal<File | null>(null);
  uploadingFile  = signal(false);

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

  instructionTypes  = signal<any[]>([]);
  availableAgents   = signal<{ id: string; nomComplet: string }[]>([]);

  // ── Computed : utilisateur courant ────────────────────────────────────────

  readonly currentUser = computed(() => this.auth.currentUser());

  /** Peut créer une nouvelle instruction (non-SUBORDONNE) */
  readonly peutCreerInstruction = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    return this.auth.hasPermission('CAN_CREATE_INSTRUCTION');
  });

  /** Thread actif est de type DOCUMENTAIRE */
  readonly isDocumentaire = computed(() =>
    this.activeThread()?.typeInstruction === 'DOCUMENTAIRE'
  );

  /** L'utilisateur courant peut clôturer manuellement l'instruction active */
  readonly peutCloturerManuellement = computed(() => {
    const thread = this.activeThread();
    const user   = this.currentUser();
    if (!thread || !user) return false;
    if (thread.statut === 'CLOTURE') return false;
    // Seul l'initiateur peut clôturer une instruction LIBRE
    if (thread.typeInstruction === 'DOCUMENTAIRE') return false;
    const canClose = this.auth.hasPermission('CAN_CLOSE');
    const isCreateur = thread.createdById && thread.createdById === user.id;
    return canClose || isCreateur;
  });

  // ── Maps de présentation ──────────────────────────────────────────────────

  readonly statutColors: Record<string, string> = {
    'OUVERT':   'bg-gray-100 text-gray-700',
    'EN_COURS': 'bg-yellow-100 text-yellow-700',
    'CLOTURE':  'bg-green-100 text-green-700',
  };

  readonly statutLabels: Record<string, string> = {
    'OUVERT':   'Ouvert',
    'EN_COURS': 'En cours',
    'CLOTURE':  'Clôturé',
  };

  readonly urgenceConfig: Record<string, { label: string; cls: string }> = {
    URGENT:   { label: 'Urgent',   cls: 'bg-red-100 text-red-800' },
    NORMAL:   { label: 'Normal',   cls: 'bg-gray-100 text-gray-700' },
    PLANIFIE: { label: 'Planifié', cls: 'bg-blue-100 text-blue-700' },
  };

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    this.chargerThreads();
    this.api.getInstructionTypes(true).subscribe(types => {
      this.instructionTypes.set(types);
      if (types.length > 0) this.newInstructionTypeId.set(types[0].id);
    });
    this.api.getUsers().subscribe(list => {
      this.availableAgents.set(
        list
          .filter((u: any) => u.actif !== false)
          .map((u: any) => ({ id: u.id, nomComplet: u.nomComplet as string }))
          .filter((u: any) => !!u.nomComplet)
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
      this.threads.update(ts => ts.map(t =>
        t.id === data.id ? { ...t, statut: data.statut ?? t.statut } : t
      ));
      if (this.activeThreadId() === data.id) {
        this.rafraichirMessages();
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

  getBadgeLabel(thread: any): string {
    return this.statutLabels[thread.statut] ?? thread.statut ?? '';
  }

  getBadgeClasses(thread: any): string {
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
    return {
      id:             m.id,
      sender:         m.sender,
      text:           m.text ?? '',
      time:           m.time,
      isSelf:         m.isSelf,
      isSystemMessage: m.systemMessage ?? false,
      hasAttachment:  m.hasAttachment,
      attachmentName: m.attachmentName,
      audioUrl:       m.audioUrl,
      highlightsJson: m.highlightsJson,
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
    this.isNewInstructionModalOpen.set(true);
  }

  onTypeSelected(typeId: string) {
    this.newInstructionTypeId.set(typeId);
    const itype = this.instructionTypes().find(t => t.id === typeId);
    if (itype) this.newInstructionUrgence.set(itype.urgenceDefaut);
  }

  addAssignee() {
    const agents = this.availableAgents();
    const first  = agents[0];
    this.newInstructionAssignees.update(list => [
      ...list, { agent: first?.nomComplet ?? '', userId: first?.id }
    ]);
  }

  removeAssignee(index: number) {
    this.newInstructionAssignees.update(list => list.filter((_, i) => i !== index));
  }

  updateAssigneeAgent(index: number, nomComplet: string) {
    const user = this.availableAgents().find(u => u.nomComplet === nomComplet);
    this.newInstructionAssignees.update(list => {
      const next = [...list];
      next[index] = { agent: nomComplet, userId: user?.id };
      return next;
    });
  }

  closeNewInstructionModal() {
    this.isNewInstructionModalOpen.set(false);
  }

  createNewInstruction() {
    if (!this.newInstructionMessage().trim() && !this.hasAudioRecord()) return;
    const payload = {
      title:             this.newInstructionTitle().trim() || '',
      instructionTypeId: this.newInstructionTypeId(),
      urgence:           this.newInstructionUrgence(),
      echeance:          this.newInstructionEcheance() || null,
      confidentialite:   this.newInstructionConfidential(),
      message:           this.newInstructionMessage(),
      assignees:         this.newInstructionAssignees().map(a => ({
        agent:  a.agent,
        userId: a.userId ?? null,
      })),
      hasAudio: this.hasAudioRecord(),
    };
    this.api.createInstruction(payload).subscribe({
      next: created => {
        const newThread = { ...created, active: true, unread: 0 };
        this.threads.update(t => [newThread, ...t.map(th => ({ ...th, active: false }))]);
        this.chargerMessages(created.id);
        this.closeNewInstructionModal();
      },
      error: err => this.toast.error(err?.error?.error ?? "Impossible de créer l'instruction."),
    });
  }

  // ── Envoi de messages ─────────────────────────────────────────────────────

  sendNormalMessage() {
    if (!this.newMessage().trim()) return;
    this.posterMessage();
  }

  private posterMessage(attachmentName?: string) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const user   = this.currentUser();
    const isSelf = true;

    const payload: any = {
      sender: user?.nomComplet || 'Inconnu',
      isSelf,
      text:   this.newMessage(),
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

  // ── Clôture manuelle (instructions LIBRE) ────────────────────────────────

  cloturerInstruction() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.cloturerInstruction(id).subscribe({
      next: updated => {
        this.threads.update(ts => ts.map(t =>
          t.id === id ? { ...t, statut: 'CLOTURE' } : t
        ));
        this.rafraichirMessages();
        this.toast.success('Instruction clôturée.');
      },
      error: err => this.toast.error(err?.error?.error ?? 'Clôture refusée.'),
    });
  }

  // ── Navigation Bureau (instructions DOCUMENTAIRE) ─────────────────────────

  produireDocument() {
    const thread = this.activeThread();
    if (!thread) return;
    this.router.navigate(['/bureau'], {
      queryParams: {
        typeDocumentId:    thread.typeDocumentAttenduId,
        sourceInstructionId: thread.id,
      }
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
        sender:         user?.nomComplet || 'DG',
        isSelf:         true,
        text:           '',
        attachmentName: res.name,
        audioUrl:       this.api.getFileUrl(res.name),
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
