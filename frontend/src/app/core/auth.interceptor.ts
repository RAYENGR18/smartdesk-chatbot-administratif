import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';

/**
 * Ajoute le token JWT ("Authorization: Bearer ...") aux appels vers l'API
 * du backend, et renvoie vers la page de connexion si la session a expiré.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const apiBase = environment.apiBaseUrl;
  const isApiCall = req.url.startsWith(`${apiBase}/api/`) || req.url.startsWith('/api/');
  const isAuthCall = req.url.includes('/api/auth/');
  const token = auth.getToken();

  const request = isApiCall && token && !isAuthCall
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && isApiCall && !isAuthCall) {
        auth.logout();
        router.navigate(['/login'], { queryParams: { expired: 1 } });
      }
      return throwError(() => error);
    })
  );
};
