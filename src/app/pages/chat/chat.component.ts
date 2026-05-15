import { Component, OnInit, signal, ViewChild, ElementRef, AfterViewChecked, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

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
}

interface Assignee {
  agent: string;
  role: 'action_signature' | 'action_courrier' | 'action_note' | 'avis_simple';
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, AfterViewChecked {
  @ViewChild('chatContainer') chatContainer!: ElementRef;

  private api = inject(ApiService);

  mobileView = signal<'list' | 'chat'>('list');
  threads = signal<any[]>([]);
  activeThreadId = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  newMessage = signal('');
  showFinalActionMenu = signal(false);
  private shouldScrollToBottom = false;

  isNewInstructionModalOpen = signal(false);
  newInstructionTitle = signal('');
  newInstructionType = signal('');
  newInstructionMessage = signal('');
  newInstructionAssignees = signal<Assignee[]>([]);
  isRecordingAudio = signal(false);
  hasAudioRecord = signal(false);

  instructionTypes = [
    'Préparation de document', 'Demande de rapport', 'Organisation de réunion',
    'Suivi de projet', 'Validation financière', 'Autre'
  ];

  availableAgents = ['Sophie Martin', 'Jean Dupont', 'Marc Lemaire', 'Alice Dubois'];

  availableRoles = [
    { value: 'action_signature', label: 'Action : Document pour signature' },
    { value: 'action_courrier', label: 'Action : Dépôt Courrier Arrivé' },
    { value: 'action_note', label: 'Action : Rédiger Note de service' },
    { value: 'avis_simple', label: 'Avis : Émettre un avis (Consultatif)' }
  ];

  ngOnInit() {
    this.api.getInstructions().subscribe(list => {
      const threads = list.map((t, i) => ({ ...t, active: i === 0 }));
      this.threads.set(threads);
      if (threads.length > 0) {
        this.loadMessages(threads[0].id);
      }
    });
  }

  private loadMessages(threadId: string) {
    this.activeThreadId.set(threadId);
    this.api.getMessages(threadId).subscribe(msgs => {
      this.messages.set(msgs.map(m => ({
        id: m.id,
        sender: m.sender,
        text: m.text,
        time: m.time,
        isSelf: m.isSelf,
        type: m.type as 'normal' | 'final',
        hasAttachment: m.hasAttachment,
        attachmentName: m.attachmentName,
        actionType: m.actionType,
        status: m.status as any
      })));
      this.shouldScrollToBottom = true;
    });
  }

  selectThread(id: string) {
    this.threads.update(t => t.map(thread => ({ ...thread, active: thread.id === id })));
    this.loadMessages(id);
    this.mobileView.set('chat');
  }

  toggleActionMenu() {
    this.showFinalActionMenu.set(!this.showFinalActionMenu());
  }

  sendNormalMessage() {
    if (this.newMessage().trim() === '') return;
    this.postMessage('normal');
  }

  sendFinalAction(actionType: string) {
    if (this.newMessage().trim() === '') return;
    this.postMessage('final', actionType);
    this.showFinalActionMenu.set(false);
  }

  private postMessage(type: 'normal' | 'final', actionType?: string) {
    const threadId = this.activeThreadId();
    if (!threadId) return;
    const isSelf = type === 'normal';
    const payload = {
      sender: isSelf ? 'DG' : 'Sophie Martin (Agent)',
      isSelf,
      text: this.newMessage(),
      type: type.toUpperCase(),
      actionType: actionType?.toUpperCase()
    };
    this.api.sendMessage(threadId, payload).subscribe(saved => {
      this.messages.update(msgs => [...msgs, {
        id: saved.id,
        sender: saved.sender,
        text: saved.text,
        time: saved.time,
        isSelf: saved.isSelf,
        type: saved.type as 'normal' | 'final',
        hasAttachment: saved.hasAttachment,
        attachmentName: saved.attachmentName,
        actionType: saved.actionType,
        status: saved.status as any
      }]);
      this.newMessage.set('');
      this.shouldScrollToBottom = true;
    });
  }

  validateFinalAction(msgId: string) {
    this.api.validateMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m => m.id === msgId ? { ...m, status: 'validated' } : m));
    });
  }

  rejectFinalAction(msgId: string) {
    this.api.rejectMessage(msgId).subscribe(() => {
      this.messages.update(msgs => msgs.map(m => m.id === msgId ? { ...m, status: 'rejected' } : m));
    });
  }

  openNewInstructionModal() {
    this.isNewInstructionModalOpen.set(true);
    this.newInstructionTitle.set('');
    this.newInstructionType.set(this.instructionTypes[0]);
    this.newInstructionAssignees.set([{ agent: this.availableAgents[0], role: 'action_signature' }]);
    this.newInstructionMessage.set('');
    this.isRecordingAudio.set(false);
    this.hasAudioRecord.set(false);
  }

  addAssignee() {
    this.newInstructionAssignees.update(list => [...list, { agent: this.availableAgents[0], role: 'avis_simple' }]);
  }

  removeAssignee(index: number) {
    this.newInstructionAssignees.update(list => list.filter((_, i) => i !== index));
  }

  updateAssigneeAgent(index: number, newAgent: string) {
    this.newInstructionAssignees.update(list => {
      const next = [...list];
      next[index] = { ...next[index], agent: newAgent };
      return next;
    });
  }

  updateAssigneeRole(index: number, newRole: any) {
    this.newInstructionAssignees.update(list => {
      const next = [...list];
      next[index] = { ...next[index], role: newRole };
      return next;
    });
  }

  closeNewInstructionModal() {
    this.isNewInstructionModalOpen.set(false);
  }

  toggleAudioRecording() {
    if (this.isRecordingAudio()) {
      this.isRecordingAudio.set(false);
      this.hasAudioRecord.set(true);
    } else {
      this.isRecordingAudio.set(true);
      this.hasAudioRecord.set(false);
    }
  }

  createNewInstruction() {
    if (!this.newInstructionMessage().trim() && !this.hasAudioRecord()) return;
    const assignees = this.newInstructionAssignees();
    const payload = {
      title: this.newInstructionTitle().trim() || '',
      type: this.newInstructionType(),
      message: this.newInstructionMessage(),
      assignees: assignees.map(a => ({ agent: a.agent, role: a.role })),
      hasAudio: this.hasAudioRecord()
    };
    this.api.createInstruction(payload).subscribe(created => {
      const newThread = { ...created, active: true, unread: 0 };
      this.threads.update(t => [newThread, ...t.map(th => ({ ...th, active: false }))]);
      this.loadMessages(created.id);
      this.closeNewInstructionModal();
    });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendNormalMessage();
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom() {
    try {
      this.chatContainer.nativeElement.scrollTop = this.chatContainer.nativeElement.scrollHeight;
    } catch (err) { }
  }
}
