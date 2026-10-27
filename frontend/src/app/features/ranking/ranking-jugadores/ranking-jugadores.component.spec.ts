import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RankingJugadoresComponent } from './ranking-jugadores.component';
import { AuthService } from '../../../core/services/auth.service';
import { RankingService } from '../../../core/services/ranking.service';
import { RankingJugadorResponse } from '../../../shared/models/ranking.model';
import { UsuarioResponse } from '../../../shared/models/usuario.model';

const lucia: UsuarioResponse = {
  id: 3, nombre: 'Lucía', apellido: 'Fernández', email: 'lucia@test.com', telefono: null, rol: 'JUGADOR', activo: true,
  emailVerificado: true
};

function fila(id: number, jugadorId: number, jugadorNombre: string): RankingJugadorResponse {
  return { id, jugadorId, jugadorNombre, categoria: 'CUARTA', puntos: 12 - id, partidosJugados: 4, partidosGanados: 3 };
}

describe('RankingJugadoresComponent', () => {
  it('reconoce la fila propia por jugadorId (el ranking no trae emails)', () => {
    const ranking = [fila(1, 2, 'Juan Jugador'), fila(2, 3, 'Lucía Fernández'), fila(3, 201, 'Martina López')];
    TestBed.configureTestingModule({
      imports: [RankingJugadoresComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: signal(lucia) } },
        { provide: RankingService, useValue: { obtenerRankingPorCategoria: () => of(ranking) } }
      ]
    });

    const fixture = TestBed.createComponent(RankingJugadoresComponent);
    fixture.detectChanges();

    const filas: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
    expect(filas.map(f => f.textContent?.includes('Lucía Fernández'))).toEqual([false, true, false]);
    expect(filas.map(f => f.textContent?.includes('Vos'))).toEqual([false, true, false]);
  });
});
