import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SignatureModalComponent } from '../../components/signature-modal/signature-modal.component';

@Component({
  selector: 'app-signature',
  standalone: true,
  imports: [CommonModule, SignatureModalComponent],
  templateUrl: './signature.component.html',
  styleUrl: './signature.component.css'
})
export class SignatureComponent {
  isModalOpen = signal(false);
  isSigned = signal(false);
  signatureImage = signal<string | null>(null);

  openModal() {
    this.isModalOpen.set(true);
  }

  closeModal() {
    this.isModalOpen.set(false);
  }

  handleValidation(signatureDataUrl: string) {
    this.signatureImage.set(signatureDataUrl);
    this.isSigned.set(true);
    this.isModalOpen.set(false);
    // Futur appel Backend pour injecter l'image dans le vrai PDF (Spring Boot)
  }
}
