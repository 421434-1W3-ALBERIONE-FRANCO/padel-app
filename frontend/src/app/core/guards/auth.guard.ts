import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Guard para proteger rutas que requieren estar autenticado.
 * Opcionalmente verifica si el usuario tiene los roles esperados.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    // Si la ruta requiere roles específicos
    const expectedRoles = route.data?.['roles'] as string[];
    if (expectedRoles && expectedRoles.length > 0) {
      const userRole = authService.userRole();
      if (!userRole || !expectedRoles.includes(userRole)) {
        // Redirigir si no tiene permisos
        router.navigate(['/dashboard']);
        return false;
      }
    }
    return true;
  }

  // Redirigir al login si no está autenticado
  router.navigate(['/auth/login']);
  return false;
};

/**
 * Guard para evitar que usuarios ya autenticados accedan a login/registro.
 */
export const guestGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    router.navigate(['/dashboard']);
    return false;
  }
  return true;
};
