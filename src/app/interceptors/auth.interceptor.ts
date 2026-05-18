import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('dg_token');

  const authReq = (token && !req.url.includes('/api/auth/login'))
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.includes('/api/auth/login')) {
        localStorage.removeItem('dg_token');
        localStorage.removeItem('dg_user');
        inject(Router).navigate(['/login']);
      }
      return throwError(() => err);
    })
  );
};
