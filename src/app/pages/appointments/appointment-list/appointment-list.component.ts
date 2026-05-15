import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './appointment-list.component.html'
})
export class AppointmentListComponent implements OnInit {
  private api = inject(ApiService);

  rendezVous = signal<any[]>([]);
  loading = signal(true);
  viewMode = signal<'list' | 'calendar'>('list');
  currentMonthDate = signal(new Date());
  selectedDay = signal<string | null>(null);

  today = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  todayStr = this.toDateStr(new Date());

  readonly weekHeaders = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  statutColors: Record<string, string> = {
    'PLANIFIE': 'bg-blue-100 text-blue-700',
    'EN_COURS': 'bg-green-100 text-green-700',
    'TERMINE':  'bg-gray-100 text-gray-600',
    'ANNULE':   'bg-red-100 text-red-700'
  };

  statutLabels: Record<string, string> = {
    'PLANIFIE': 'Planifié',
    'EN_COURS': 'En cours',
    'TERMINE':  'Terminé',
    'ANNULE':   'Annulé'
  };

  rdvByDay = computed(() => {
    const map: Record<string, any[]> = {};
    for (const r of this.rendezVous()) {
      if (r.date) {
        const key = (r.date as string).substring(0, 10);
        (map[key] ??= []).push(r);
      }
    }
    return map;
  });

  calendarDays = computed(() => {
    const d = this.currentMonthDate();
    const year = d.getFullYear();
    const month = d.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDow = (firstDay.getDay() + 6) % 7;
    const cells: Array<{ date: string; day: number } | null> = [];
    for (let i = 0; i < startDow; i++) cells.push(null);
    for (let day = 1; day <= lastDay.getDate(); day++) {
      cells.push({
        date: `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`,
        day
      });
    }
    while (cells.length % 7 !== 0) cells.push(null);
    return cells;
  });

  filteredRdv = computed(() => {
    const day = this.selectedDay();
    if (!day) return this.rendezVous();
    return this.rendezVous().filter(r => (r.date as string)?.substring(0, 10) === day);
  });

  currentMonthLabel = computed(() =>
    this.currentMonthDate().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })
  );

  selectedDayLabel = computed(() => {
    const day = this.selectedDay();
    if (!day) return null;
    const d = new Date(day + 'T12:00:00');
    return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  });

  ngOnInit() {
    this.api.getRendezVous().subscribe(data => {
      this.rendezVous.set(data);
      this.loading.set(false);
    });
  }

  switchView(mode: 'list' | 'calendar') {
    this.viewMode.set(mode);
    this.selectedDay.set(null);
  }

  selectDay(dateStr: string) {
    this.selectedDay.set(this.selectedDay() === dateStr ? null : dateStr);
  }

  prevMonth() {
    const d = new Date(this.currentMonthDate());
    d.setMonth(d.getMonth() - 1);
    this.currentMonthDate.set(d);
    this.selectedDay.set(null);
  }

  nextMonth() {
    const d = new Date(this.currentMonthDate());
    d.setMonth(d.getMonth() + 1);
    this.currentMonthDate.set(d);
    this.selectedDay.set(null);
  }

  private toDateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
