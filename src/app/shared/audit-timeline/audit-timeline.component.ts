import { Component, Input, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-audit-timeline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit-timeline.component.html'
})
export class AuditTimelineComponent implements OnInit {
  @Input() entityType = '';
  @Input() entityId   = '';

  private api = inject(ApiService);
  logs = signal<any[]>([]);
  loading = signal(true);

  readonly actionIcons: Record<string, string> = {
    'CREER':          '✏️',
    'MODIFIER':       '🔄',
    'SOUMETTRE':      '📤',
    'VALIDER':        '✅',
    'REJETER':        '❌',
    'STATUT_TRAITE':  '✔️',
    'STATUT_EN_COURS':'⚙️',
    'STATUT_EXPEDIE': '📬',
    'STATUT_SIGNE':   '✍️',
  };

  ngOnInit() {
    if (this.entityType && this.entityId) {
      this.api.getAuditByEntity(this.entityType, this.entityId).subscribe(data => {
        this.logs.set(data);
        this.loading.set(false);
      });
    } else {
      this.loading.set(false);
    }
  }

  icon(action: string): string {
    return this.actionIcons[action] ?? '📋';
  }

  formatDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' })
      + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }
}
