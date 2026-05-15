import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SignatureModalComponent } from '../../components/signature-modal/signature-modal.component';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-signature',
  standalone: true,
  imports: [CommonModule, SignatureModalComponent],
  templateUrl: './signature.component.html',
  styleUrl: './signature.component.css'
})
export class SignatureComponent {
  private api = inject(ApiService);

  isModalOpen  = signal(false);
  isSigned     = signal(false);
  isSaving     = signal(false);
  signatureImage = signal<string | null>(null);
  signatureUrl   = signal<string | null>(null);
  signedAt       = signal<string | null>(null);

  openModal()  { this.isModalOpen.set(true); }
  closeModal() { this.isModalOpen.set(false); }

  handleValidation(dataUrl: string) {
    this.isModalOpen.set(false);
    this.signatureImage.set(dataUrl);
    this.isSaving.set(true);

    fetch(dataUrl)
      .then(r => r.blob())
      .then(blob => {
        const file = new File([blob], `signature_${Date.now()}.png`, { type: 'image/png' });
        this.api.uploadFile(file).subscribe({
          next: res => {
            this.signatureUrl.set(this.api.getFileUrl(res.name));
            this.isSigned.set(true);
            this.isSaving.set(false);
            this.signedAt.set(new Date().toLocaleDateString('fr-FR', {
              day: '2-digit', month: 'long', year: 'numeric',
              hour: '2-digit', minute: '2-digit'
            }));
          },
          error: () => {
            this.isSigned.set(true);
            this.isSaving.set(false);
            this.signedAt.set(new Date().toLocaleDateString('fr-FR', {
              day: '2-digit', month: 'long', year: 'numeric'
            }));
          }
        });
      });
  }
}
