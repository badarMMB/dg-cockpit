import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 to-slate-700 flex items-center justify-center p-4">
      <div class="w-full max-w-sm">

        <!-- Logo -->
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 backdrop-blur mb-4">
            <span class="text-white font-bold text-2xl">DG</span>
          </div>
          <h1 class="text-white text-2xl font-bold tracking-tight">DG Cockpit</h1>
          <p class="text-slate-400 text-sm mt-1">Douanes de Djibouti</p>
        </div>

        <!-- Card -->
        <div class="bg-white rounded-2xl shadow-2xl p-8">
          <h2 class="text-gray-800 text-lg font-semibold mb-6">Connexion</h2>

          @if (errorMsg()) {
            <div class="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
              {{ errorMsg() }}
            </div>
          }

          <div class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1.5">Identifiant</label>
              <input [(ngModel)]="username"
                     type="text"
                     placeholder="Votre identifiant"
                     (keydown.enter)="doLogin()"
                     class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1.5">Mot de passe</label>
              <input [(ngModel)]="password"
                     type="password"
                     placeholder="••••••••"
                     (keydown.enter)="doLogin()"
                     class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
            </div>
          </div>

          <button (click)="doLogin()" [disabled]="loading()"
                  class="mt-6 w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold py-3 rounded-xl text-sm transition-colors">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
        </div>

        <!-- Aide discrète dev -->
        <p class="text-center text-slate-500 text-xs mt-6">
          DG : dg / dg1234 &nbsp;·&nbsp; Admin : admin / admin1234
        </p>

      </div>
    </div>
  `
})
export class LoginComponent {
  private auth   = inject(AuthService);
  private router = inject(Router);

  username = '';
  password = '';
  loading  = signal(false);
  errorMsg = signal('');

  doLogin() {
    if (!this.username || !this.password) return;
    this.loading.set(true);
    this.errorMsg.set('');

    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err: any) => {
        this.errorMsg.set(err.error?.error ?? 'Identifiants incorrects');
        this.loading.set(false);
      }
    });
  }
}
