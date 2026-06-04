import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const permissionGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();

  if (!user) return router.createUrlTree(['/login']);

  const allPermissions: string[] = route.data['permissions'] ?? [];
  const anyPermissions: string[] = route.data['anyPermissions'] ?? [];

  const hasAll = allPermissions.every(p => auth.hasPermission(p));
  const hasAny = anyPermissions.length === 0 || auth.hasAnyPermission(...anyPermissions);

  return hasAll && hasAny ? true : router.createUrlTree(['/dashboard']);
};
