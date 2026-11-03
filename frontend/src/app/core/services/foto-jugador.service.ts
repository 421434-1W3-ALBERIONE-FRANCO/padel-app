import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { MatchService } from './match.service';

/**
 * Fotos de perfil como object URLs, cacheadas por jugador y versión: la misma foto en la carta, la lista de
 * matches y el modal se descarga una sola vez. Al llegar una versión nueva se libera la anterior.
 */
@Injectable({
  providedIn: 'root'
})
export class FotoJugadorService {
  private matchService = inject(MatchService);
  private cache = new Map<string, Observable<string | null>>();
  private ultimaVersion = new Map<number, { version: number; url?: string }>();

  url(usuarioId: number, version: number): Observable<string | null> {
    const clave = `${usuarioId}:${version}`;
    let foto$ = this.cache.get(clave);
    if (!foto$) {
      foto$ = this.matchService.obtenerFoto(usuarioId, version).pipe(
        map(blob => {
          const url = URL.createObjectURL(blob);
          this.reemplazar(usuarioId, version, url);
          return url;
        }),
        // Sin permiso o ya borrada: se muestran las iniciales.
        catchError(() => of(null)),
        shareReplay(1)
      );
      this.cache.set(clave, foto$);
    }
    return foto$;
  }

  private reemplazar(usuarioId: number, version: number, url: string): void {
    const anterior = this.ultimaVersion.get(usuarioId);
    if (anterior && anterior.version !== version && anterior.url) {
      URL.revokeObjectURL(anterior.url);
      this.cache.delete(`${usuarioId}:${anterior.version}`);
    }
    this.ultimaVersion.set(usuarioId, { version, url });
  }
}
