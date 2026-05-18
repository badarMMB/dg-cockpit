import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

export interface AppUser {
  id: string;
  username: string;
  nomComplet: string;
  role: 'DG' | 'SECRETAIRE' | 'SUBORDONNE' | 'ADMIN_IT';
  actif: boolean;
}

const TOKEN_KEY = 'dg_token';
const USER_KEY  = 'dg_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http   = inject(HttpClient);
  private router = inject(Router);

  currentUser = signal<AppUser | null>(this.loadUser());

  private loadUser(): AppUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  isAuthenticated(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  login(username: string, password: string): Observable<any> {
    return this.http.post<{ token: string; user: AppUser }>('/api/auth/login', { username, password }).pipe(
      tap(res => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, JSON.stringify(res.user));
        this.currentUser.set(res.user);
      })
    );
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}).subscribe();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(...roles: AppUser['role'][]): boolean {
    const user = this.currentUser();
    return user ? roles.includes(user.role) : false;
  }
}
