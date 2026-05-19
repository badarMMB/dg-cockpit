import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  const allowedRoles: string[] = route.data['roles'] ?? [];
  const role = auth.currentUser()?.role ?? '';

  if (allowedRoles.length === 0 || allowedRoles.includes(role)) return true;
  return router.createUrlTree(['/dashboard']);
};
