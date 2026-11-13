import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Page de destination par défaut selon le rôle. */
export function homeForRole(role: string | null): string {
  switch (role) {
    case 'ADMIN': return '/admin';
    case 'PROFESSEUR': return '/professeur';
    case 'USER': return '/etudiant';
    default: return '/login';
  }
}

/**
 * Protège une route : connexion obligatoire et, si "roles" est fourni,
 * rôle autorisé. La vraie vérification reste faite par le backend ;
 * ce guard évite simplement d'afficher des pages inaccessibles.
 */
export function roleGuard(...roles: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isLoggedIn()) {
      auth.logout();
      return router.createUrlTree(['/login']);
    }
    const role = auth.getRole();
    if (roles.length > 0 && (!role || !roles.includes(role))) {
      return router.createUrlTree([homeForRole(role)]);
    }
    return true;
  };
}

/** Pages publiques (connexion, inscription) : un utilisateur connecté est redirigé. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? router.createUrlTree([homeForRole(auth.getRole())]) : true;
};
