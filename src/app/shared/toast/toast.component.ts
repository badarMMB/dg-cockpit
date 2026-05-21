import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed bottom-5 right-5 z-[9999] flex flex-col gap-2 pointer-events-none">
      @for (t of toast.toasts(); track t.id) {
        <div class="flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border text-sm font-medium
                    max-w-sm w-full pointer-events-auto animate-slide-up"
             [ngClass]="{
               'bg-emerald-50 border-emerald-200 text-emerald-800': t.type === 'success',
               'bg-red-50   border-red-200   text-red-800':         t.type === 'error',
               'bg-blue-50  border-blue-200  text-blue-800':        t.type === 'info',
               'bg-amber-50 border-amber-200 text-amber-800':       t.type === 'warning'
             }">
          <span class="text-base flex-shrink-0 mt-px">
            {{ t.type === 'success' ? '✓' : t.type === 'error' ? '✕' : t.type === 'warning' ? '⚠' : 'ℹ' }}
          </span>
          <span class="flex-1 leading-snug">{{ t.message }}</span>
          <button (click)="toast.dismiss(t.id)"
                  class="flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity text-base leading-none">
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slide-up {
      from { opacity: 0; transform: translateY(12px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    .animate-slide-up { animation: slide-up 0.2s ease-out; }
  `]
})
export class ToastComponent {
  toast = inject(ToastService);
}
