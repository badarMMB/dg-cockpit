import { Injectable, signal } from '@angular/core';

export interface Notification {
  type: string;
  [key: string]: any;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  notifications = signal<Notification[]>([]);
  unreadCount = signal(0);

  private source: EventSource | null = null;

  connect() {
    if (this.source && this.source.readyState !== EventSource.CLOSED) return;

    this.source = new EventSource('/api/events');

    this.source.addEventListener('FILE_UPLOADED', (e: MessageEvent) => {
      this.push(JSON.parse(e.data));
    });

    this.source.addEventListener('COURRIER_ARRIVE', (e: MessageEvent) => {
      this.push(JSON.parse(e.data));
    });

    this.source.addEventListener('STATUT_CHANGE', (e: MessageEvent) => {
      this.push(JSON.parse(e.data));
    });

    // reconnexion automatique gérée nativement par EventSource
  }

  private push(data: any) {
    const notif: Notification = { ...data, timestamp: Date.now() };
    this.notifications.update(n => [notif, ...n].slice(0, 50));
    this.unreadCount.update(c => c + 1);
  }

  clearUnread() {
    this.unreadCount.set(0);
  }

  disconnect() {
    this.source?.close();
    this.source = null;
  }
}
