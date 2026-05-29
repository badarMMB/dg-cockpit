import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const bureauGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  // Tout utilisateur connecté peut accéder au bureau (docs de transit inclus)
  return auth.currentUser() !== null ? true : router.createUrlTree(['/dashboard']);
};
