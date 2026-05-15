import { Component, Output, EventEmitter, signal, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-audio-recorder',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audio-recorder.component.html'
})
export class AudioRecorderComponent implements OnDestroy {
  @Output() recorded = new EventEmitter<Blob>();

  isRecording = signal(false);
  seconds = signal(0);
  error = signal('');

  private mediaRecorder?: MediaRecorder;
  private chunks: Blob[] = [];
  private timer?: ReturnType<typeof setInterval>;

  async startRecording() {
    this.error.set('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.chunks = [];
      this.mediaRecorder = new MediaRecorder(stream);
      this.mediaRecorder.ondataavailable = e => { if (e.data.size > 0) this.chunks.push(e.data); };
      this.mediaRecorder.onstop = () => {
        const blob = new Blob(this.chunks, { type: 'audio/webm' });
        this.recorded.emit(blob);
        stream.getTracks().forEach(t => t.stop());
      };
      this.mediaRecorder.start();
      this.isRecording.set(true);
      this.seconds.set(0);
      this.timer = setInterval(() => this.seconds.update(s => s + 1), 1000);
    } catch {
      this.error.set('Microphone non disponible');
    }
  }

  stopRecording() {
    clearInterval(this.timer);
    this.mediaRecorder?.stop();
    this.isRecording.set(false);
  }

  cancelRecording() {
    clearInterval(this.timer);
    if (this.mediaRecorder?.state !== 'inactive') {
      this.mediaRecorder?.stream?.getTracks().forEach(t => t.stop());
    }
    this.isRecording.set(false);
    this.seconds.set(0);
  }

  formatTime(s: number): string {
    const m = Math.floor(s / 60);
    return `${m}:${String(s % 60).padStart(2, '0')}`;
  }

  ngOnDestroy() {
    clearInterval(this.timer);
    this.mediaRecorder?.stream?.getTracks().forEach(t => t.stop());
  }
}
