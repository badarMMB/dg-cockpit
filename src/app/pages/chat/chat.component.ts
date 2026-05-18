import { Component, OnInit, signal, ViewChild, ElementRef, AfterViewChecked, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { WorkflowTimelineComponent } from '../../shared/workflow-timeline/workflow-timeline.component';
import { AudioRecorderComponent } from '../../shared/audio-recorder/audio-recorder.component';

interface ChatMessage {
  id: string;
  sender: string;
  text: string;
  time: string;
  isSelf: boolean;
  type: 'normal' | 'final';
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
  imports: [CommonModule, FormsModule, WorkflowTimelineComponent, AudioRecorderComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, AfterViewChecked {
  @ViewChild('chatContainer') chatContainer!: ElementRef;

  private api  = inject(ApiService);
  private auth = inject(AuthService);

  mobileView       = signal<'list' | 'chat'>('list');
  threads          = signal<any[]>([]);
  activeThreadId   = signal<string | null>(null);
  messages         = signal<ChatMessage[]>([]);
  newMessage       = signal('');
  showFinalActionMenu = signal(false);
  private shouldScrollToBottom = false;

  // ── New instruction modal ─────────────────────────────────────────────────
  isNewInstructionModalOpen  = signal(false);
  newInstructionTitle        = signal('');
  newInstructionTypeId       = signal('');   // selected InstructionType ID
  newInstructionUrgence      = signal('NORMAL');
  newInstructionEcheance     = signal('');
  newInstructionConfidential = signal(false);
  newInstructionMessage      = signal('');
  newInstructionAssignees    = signal<Assignee[]>([]);
  isRecordingAudio           = signal(false);
  hasAudioRecord             = signal(false);

  // ── Reference data ────────────────────────────────────────────────────────
  instructionTypes  = signal<any[]>([]);   // loaded from API
  availableAgents   = signal<string[]>([]); // loaded from Collaborateurs

  // ── Workflow ──────────────────────────────────────────────────────────────
  workflowSteps = signal<any[]>([]);
  showWorkflow  = signal(false);

  currentUser = computed(() => this.auth.currentUser());
  isDG        = computed(() => this.currentUser()?.role === 'DG');

  // ── Maps ──────────────────────────────────────────────────────────────────
  readonly statutColors: Record<string, string> = {
    'OUVERT':            'bg-gray-100 text-gray-700',
    'EN_COURS':          'bg-yellow-100 text-yellow-700',
    'EN_ATTENTE':        'bg-orange-100 text-orange-700',
    'SOUMIS_VALIDATION': 'bg-blue-100 text-blue-700',
    'CLOTURE':           'bg-green-100 text-green-700',
    'REFUSE':            'bg-red-100 text-red-700',
  };

  readonly statutLabels: Record<string, string> = {
    'OUVERT':            'Ouvert',
    'EN_COURS':          'En cours',
    'EN_ATTENTE':        'En attente',
    'SOUMIS_VALIDATION': 'En validation',
    'CLOTURE':           'Clôturé',
    'REFUSE':            'Refusé',
  };

  readonly urgenceConfig: Record<string, { label: string; cls: string }> = {
    URGENT:   { label: 'Urgent',   cls: 'bg-red-100 text-red-800' },
    NORMAL:   { label: 'Normal',   cls: 'bg-gray-100 text-gray-700' },
    PLANIFIE: { label: 'Planifié', cls: 'bg-blue-100 text-blue-700' },
  };

  availableRoles = [
    { value: 'action_signature', label: 'Action : Document pour signature' },
    { value: 'action_courrier',  label: 'Action : Dépôt Courrier Arrivé' },
    { value: 'action_note',      label: 'Action : Rédiger Note de service' },
    { value: 'avis_simple',      label: 'Avis : Émettre un avis (Consultatif)' },
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    this.loadThreads();
    this.api.getInstructionTypes(true).subscribe(types => {
      this.instructionTypes.set(types);
      if (types.length > 0) this.newInstructionTypeId.set(types[0].id);
    });
    this.api.getCollaborateurs().subscribe(list => {
      this.availableAgents.set(list.map((c: any) => c.name));
    });
  }

  private loadThreads() {
    this.api.getInstructions().subscribe(list => {
      const threads = list.map((t: any, i: number) => ({ ...t, active: i === 0 }));
      this.threads.set(threads);
      if (threads.length > 0) this.loadMessages(threads[0].id);
    });
  }

  private loadMessages(threadId: string) {
    this.activeThreadId.set(threadId);
    this.showWorkflow.set(false);
    this.api.getMessages(threadId).subscribe(msgs => {
      this.messages.set(msgs.map((m: any) => ({
        id: m.id, sender: m.sender, text: m.text, time: m.time,
        isSelf: m.isSelf, type: m.type as 'normal' | 'final',
        hasAttachment: m.hasAttachment, attachmentName: m.attachmentName,
        actionType: m.actionType, status: m.status as any, audioUrl: m.audioUrl,
      })));
      this.shouldScrollToBottom = true;
    });
    this.api.getWorkflow(threadId).subscribe(steps => this.workflowSteps.set(steps));
  }

  selectThread(id: string) {
    this.threads.update(t => t.map(th => ({ ...th, active: th.id === id })));
    this.loadMessages(id);
    this.mobileView.set('chat');
  }

  // ── Modal helpers ─────────────────────────────────────────────────────────

  openNewInstructionModal() {
    const types = this.instructionTypes();
    const firstType = types[0];
    this.newInstructionTitle.set('');
    this.newInstructionTypeId.set(firstType?.id ?? '');
    this.newInstructionUrgence.set(firstType?.urgenceDefaut ?? 'NORMAL');
    this.newInstructionEcheance.set('');
    this.newInstructionConfidential.set(false);
    this.newInstructionMessage.set('');
    const agents = this.availableAgents();
    this.newInstructionAssignees.set(agents.length
      ? [{ agent: agents[0], role: 'action_signature' }]
      : []);
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

  closeNewInstructionModal() { this.isNewInstructionModalOpen.set(false); }

  createNewInstruction() {
    if (!this.newInstructionMessage().trim() && !this.hasAudioRecord()) return;
    const payload = {
      title:              this.newInstructionTitle().trim() || '',
      instructionTypeId:  this.newInstructionTypeId(),
      urgence:            this.newInstructionUrgence(),
      echeance:           this.newInstructionEcheance() || null,
      confidentialite:    this.newInstructionConfidential(),
      message:            this.newInstructionMessage(),
      assignees:          this.newInstructionAssignees().map(a => ({ agent: a.agent, role: a.role })),
      hasAudio:           this.hasAudioRecord(),
    };
    this.api.createInstruction(payload).subscribe(created => {
      const newThread = { ...created, active: true, unread: 0 };
      this.threads.update(t => [newThread, ...t.map(th => ({ ...th, active: false }))]);
      this.loadMessages(created.id);
      this.closeNewInstructionModal();
    });
  }

  // ── Messaging ─────────────────────────────────────────────────────────────

  toggleActionMenu() { this.showFinalActionMenu.set(!this.showFinalActionMenu()); }

  sendNormalMessage() {
    if (!this.newMessage().trim()) return;
    this.postMessage('normal');
  }

  sendFinalAction(actionType: string) {
    if (!this.newMessage().trim()) return;
    this.postMessage('final', actionType);
    this.showFinalActionMenu.set(false);
  }

  private postMessage(type: 'normal' | 'final', actionType?: string) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const isSelf = type === 'normal';
    const payload = {
      sender: isSelf ? 'DG' : 'Agent',
      isSelf,
      text: this.newMessage(),
      type: type.toUpperCase(),
      actionType: actionType?.toUpperCase(),
    };
    this.api.sendMessage(threadId, payload).subscribe(saved => {
      this.messages.update(msgs => [...msgs, {
        id: saved.id, sender: saved.sender, text: saved.text, time: saved.time,
        isSelf: saved.isSelf, type: saved.type as 'normal' | 'final',
        hasAttachment: saved.hasAttachment, attachmentName: saved.attachmentName,
        actionType: saved.actionType, status: saved.status as any, audioUrl: saved.audioUrl,
      }]);
      this.newMessage.set('');
      this.shouldScrollToBottom = true;
    });
  }

  validateFinalAction(msgId: string) {
    this.api.validateMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m => m.id === msgId ? { ...m, status: 'validated' as const } : m));
    });
  }

  rejectFinalAction(msgId: string) {
    this.api.rejectMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m => m.id === msgId ? { ...m, status: 'rejected' as const } : m));
    });
  }

  onAudioRecorded(blob: Blob) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const file = new File([blob], `voice_${Date.now()}.webm`, { type: 'audio/webm' });
    this.api.uploadFile(file).subscribe(res => {
      const payload = {
        sender: 'DG', isSelf: true, text: '', type: 'NORMAL',
        attachmentName: res.name, audioUrl: this.api.getFileUrl(res.name),
      };
      this.api.sendMessage(threadId, payload).subscribe(saved => {
        this.messages.update(msgs => [...msgs, {
          id: saved.id, sender: saved.sender, text: saved.text ?? '',
          time: saved.time, isSelf: saved.isSelf, type: saved.type as 'normal' | 'final',
          hasAttachment: saved.hasAttachment, attachmentName: saved.attachmentName,
          actionType: saved.actionType, status: saved.status as any,
          audioUrl: saved.audioUrl ?? payload.audioUrl,
        }]);
        this.shouldScrollToBottom = true;
      });
    });
  }

  toggleAudioRecording() {
    this.isRecordingAudio.update(v => !v);
    if (!this.isRecordingAudio()) this.hasAudioRecord.set(true);
  }

  // ── Workflow ──────────────────────────────────────────────────────────────

  toggleWorkflow() { this.showWorkflow.update(v => !v); }

  canSoumettre(): boolean {
    const s = this.activeThread()?.statut;
    return s === 'OUVERT' || s === 'EN_COURS' || s === 'EN_ATTENTE';
  }

  canValider(): boolean { return this.activeThread()?.statut === 'SOUMIS_VALIDATION'; }

  soumettreValidation() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.soumettre(id, { validateur: 'DG', commentaire: '' }).subscribe(step => {
      this.workflowSteps.update(s => [...s, step]);
      this.threads.update(t => t.map(th => th.id === id ? { ...th, statut: 'SOUMIS_VALIDATION' } : th));
    });
  }

  validerInstruction() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.valider(id, { validateur: 'DG', commentaire: '' }).subscribe(step => {
      this.workflowSteps.update(s => [...s, step]);
      this.threads.update(t => t.map(th => th.id === id ? { ...th, statut: 'CLOTURE' } : th));
    });
  }

  rejeterInstruction() {
    const id = this.activeThreadId();
    if (!id) return;
    this.api.rejeter(id, { validateur: 'DG', commentaire: '' }).subscribe(step => {
      this.workflowSteps.update(s => [...s, step]);
      this.threads.update(t => t.map(th => th.id === id ? { ...th, statut: 'REFUSE' } : th));
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  activeThread() { return this.threads().find(t => t.id === this.activeThreadId()); }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendNormalMessage();
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      try { this.chatContainer.nativeElement.scrollTop = this.chatContainer.nativeElement.scrollHeight; }
      catch (_) {}
      this.shouldScrollToBottom = false;
    }
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
}
