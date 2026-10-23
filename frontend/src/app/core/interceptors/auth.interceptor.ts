import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

/** El token solo viaja a nuestra API: nunca a otros orígenes (fuentes, CDNs, servicios de terceros). */
function esPedidoALaApi(url: string): boolean {
  return url.startsWith(`${environment.apiUrl}/`) || url === environment.apiUrl;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toast = inject(ToastService);
  const token = esPedidoALaApi(req.url) ? authService.getToken() : null;

  let clonedRequest = req;

  if (token) {
    clonedRequest = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(clonedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401 = la sesión ya no es válida (token vencido o inválido, cuenta dada de baja): se cierra la
      // sesión local. No aplica al propio login (credenciales incorrectas muestran su error en el form).
      if (error.status === 401 && token && !req.url.endsWith('/auth/login')) {
        // Solo el primer 401 avisa: los pedidos en paralelo que fallan después ya no encuentran token.
        // Mismo texto que el backend, así el toast que muestra la pantalla con ese mensaje no se duplica.
        if (authService.getToken()) {
          toast.info(error.error?.message || 'Tu sesión expiró. Iniciá sesión nuevamente.');
        }
        authService.logout(false);
      }
      return throwError(() => error);
    })
  );
};
