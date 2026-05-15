import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './editor.component.html'
})
export class EditorComponent {
  documentTitle = signal('Note_de_service_Q3.pdf');
  documentContent = signal('Par la présente, nous actons la révision budgétaire pour le 3ème trimestre 2026.\n\nL\'ensemble des services devra reporter ses dépenses exceptionnelles avant le 15 Septembre.\n\nCordialement,');
  
  header = signal('RÉPUBLIQUE FRANÇAISE\nMINISTÈRE DES AFFAIRES GÉNÉRALES\nDirection Générale');
  footer = signal('12 Rue de la République, 75001 Paris | contact@mag.gouv.fr | +33 1 23 45 67 89');

  submitToSignature() {
    alert(`Le document "${this.documentTitle()}" a été généré en PDF avec l'en-tête officiel et envoyé dans le circuit du Parapheur pour validation DG.`);
  }
}
