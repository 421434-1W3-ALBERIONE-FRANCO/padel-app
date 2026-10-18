import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';
import { calcularTablaLiga, matrizLiga } from './tabla-liga.util';

function partido(id: number, i1: number, i2: number, sets: [number, number] | null): PartidoTorneoResponse {
  return {
    id, torneoId: 1, ronda: 'Liga', numeroRonda: 1, fechaHora: null,
    inscripcion1Id: i1, inscripcion1Nombres: `Pareja ${i1}`,
    inscripcion2Id: i2, inscripcion2Nombres: `Pareja ${i2}`,
    setsPareja1: sets ? sets[0] : null, setsPareja2: sets ? sets[1] : null,
    ganadorInscripcionId: sets ? (sets[0] > sets[1] ? i1 : i2) : null,
    estado: sets ? 'FINALIZADO' : 'PENDIENTE'
  };
}

describe('calcularTablaLiga', () => {
  it('suma 3 puntos por victoria y 1 por derrota, con sets y partidos pendientes', () => {
    const tabla = calcularTablaLiga([
      partido(1, 1, 2, [2, 0]),
      partido(2, 1, 3, [1, 2]),
      partido(3, 2, 3, [2, 1]),
      partido(4, 1, 4, null),
      partido(5, 3, 4, [2, 0])
    ]);

    expect(tabla.map(f => f.inscripcionId)).toEqual([3, 1, 2, 4]);
    const tres = tabla[0];
    expect(tres.puntos).toBe(7);
    expect([tres.jugados, tres.ganados, tres.perdidos]).toEqual([3, 2, 1]);
    expect([tres.setsFavor, tres.setsContra, tres.diferenciaSets]).toEqual([5, 3, 2]);
    expect(tres.racha).toEqual(['G', 'P', 'G']);

    const cuatro = tabla[3];
    expect(cuatro.puntos).toBe(1);
    expect(cuatro.pendientes).toBe(1);
    expect(tabla.map(f => f.posicion)).toEqual([1, 2, 3, 4]);
  });

  it('desempata dos parejas iguales en todo por el partido entre ellas', () => {
    // 1 y 2 terminan con 7 puntos, +2 y 5 sets a favor. Por nombre iría primero la 1, pero la 2 le ganó.
    const tabla = calcularTablaLiga([
      partido(1, 1, 2, [1, 2]),
      partido(2, 1, 3, [2, 0]),
      partido(3, 1, 4, [2, 1]),
      partido(4, 2, 3, [2, 0]),
      partido(5, 2, 4, [1, 2]),
      partido(6, 3, 4, [2, 1])
    ]);

    expect(tabla.map(f => f.inscripcionId)).toEqual([2, 1, 4, 3]);
    expect(tabla.slice(0, 2).map(f => [f.puntos, f.diferenciaSets, f.setsFavor])).toEqual([[7, 2, 5], [7, 2, 5]]);
  });

  it('con tres parejas empatadas no usa el enfrentamiento directo', () => {
    // Círculo de victorias: 1 le gana a 2, 2 a 3 y 3 a 1, todos 2-1.
    const tabla = calcularTablaLiga([
      partido(1, 1, 2, [2, 1]),
      partido(2, 2, 3, [2, 1]),
      partido(3, 1, 3, [1, 2])
    ]);

    expect(tabla.map(f => f.puntos)).toEqual([4, 4, 4]);
    expect(tabla.map(f => f.inscripcionId)).toEqual([1, 2, 3]);
  });
});

describe('matrizLiga', () => {
  it('muestra los sets desde el punto de vista de la fila', () => {
    const partidos = [partido(1, 1, 2, [2, 1]), partido(2, 1, 3, null)];
    const tabla = calcularTablaLiga(partidos);
    const matriz = matrizLiga(tabla, partidos);
    const fila = (id: number) => tabla.findIndex(f => f.inscripcionId === id);

    expect(matriz[fila(1)][fila(1)].tipo).toBe('propia');
    expect(matriz[fila(1)][fila(2)]).toEqual(jasmine.objectContaining({ tipo: 'ganado', texto: '2-1' }));
    expect(matriz[fila(2)][fila(1)]).toEqual(jasmine.objectContaining({ tipo: 'perdido', texto: '1-2' }));
    expect(matriz[fila(1)][fila(3)].tipo).toBe('pendiente');
    expect(matriz[fila(2)][fila(3)].tipo).toBe('sin-partido');
  });
});
