import { Component, ElementRef, EventEmitter, Output, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-signature-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './signature-modal.component.html',
  styleUrl: './signature-modal.component.css'
})
export class SignatureModalComponent implements AfterViewInit {
  @Output() close = new EventEmitter<void>();
  @Output() validate = new EventEmitter<string>();

  @ViewChild('signatureCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;
  
  private ctx!: CanvasRenderingContext2D;
  private isDrawing = false;

  ngAfterViewInit() {
    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext('2d')!;
    this.ctx.lineWidth = 4;
    this.ctx.lineCap = 'round';
    this.ctx.strokeStyle = '#00236f'; // Primary Navy Blue
  }

  startDrawing(event: MouseEvent | TouchEvent) {
    this.isDrawing = true;
    this.draw(event);
  }

  stopDrawing() {
    this.isDrawing = false;
    this.ctx.beginPath();
  }

  draw(event: MouseEvent | TouchEvent) {
    if (!this.isDrawing) return;
    
    event.preventDefault();
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    
    let clientX, clientY;
    
    if (event instanceof MouseEvent) {
      clientX = event.clientX;
      clientY = event.clientY;
    } else {
      clientX = event.touches[0].clientX;
      clientY = event.touches[0].clientY;
    }

    const x = clientX - rect.left;
    const xRatio = canvas.width / rect.width;
    const y = clientY - rect.top;
    const yRatio = canvas.height / rect.height;

    this.ctx.lineTo(x * xRatio, y * yRatio);
    this.ctx.stroke();
    this.ctx.beginPath();
    this.ctx.moveTo(x * xRatio, y * yRatio);
  }

  clear() {
    const canvas = this.canvasRef.nativeElement;
    this.ctx.clearRect(0, 0, canvas.width, canvas.height);
  }

  onValidate() {
    const canvas = this.canvasRef.nativeElement;
    const signatureDataUrl = canvas.toDataURL('image/png');
    this.validate.emit(signatureDataUrl);
  }

  onClose() {
    this.close.emit();
  }
}
