import { PartidoTorneoResponse } from '../../../shared/models/torneo.model';
import { caminoDePareja, construirCuadro, finalJugada } from './cuadro-eliminacion.util';

/** sets null = por jugar; sin rival = pase directo (como lo genera el backend). */
function partido(id: number, numeroRonda: number, ronda: string, i1: number, i2: number | null,
                 sets: [number, number] | null): PartidoTorneoResponse {
  const ganador = i2 == null ? i1 : sets ? (sets[0] > sets[1] ? i1 : i2) : null;
  return {
    id, torneoId: 1, ronda, numeroRonda, fechaHora: null,
    inscripcion1Id: i1, inscripcion1Nombres: `Pareja ${i1}`,
    inscripcion2Id: i2, inscripcion2Nombres: i2 == null ? null : `Pareja ${i2}`,
    setsPareja1: sets ? sets[0] : null, setsPareja2: sets ? sets[1] : null,
    ganadorInscripcionId: ganador, estado: ganador == null ? 'PENDIENTE' : 'FINALIZADO'
  };
}

describe('construirCuadro', () => {
  // 8 parejas: cuartos jugados y una semifinal por jugar.
  const enJuego = [
    partido(1, 1, 'Cuartos de Final', 11, 14, [2, 1]),
    partido(2, 1, 'Cuartos de Final', 16, 12, [0, 2]),
    partido(3, 1, 'Cuartos de Final', 13, 17, [2, 0]),
    partido(4, 1, 'Cuartos de Final', 18, 15, [1, 2]),
    partido(5, 2, 'Semifinal', 13, 15, [2, 1]),
    partido(6, 2, 'Semifinal', 11, 12, null)
  ];

  it('completa las rondas que faltan hasta la final', () => {
    const rondas = construirCuadro(enJuego);

    expect(rondas.map(r => r.nombre)).toEqual(['Cuartos de Final', 'Semifinal', 'Final']);
    expect(rondas.map(r => r.generada)).toEqual([true, true, false]);
    expect(rondas[2].cruces.length).toBe(1);
    expect(rondas[2].cruces[0].partido).toBeNull();
  });

  it('ordena cada ronda para que los cruces que se enfrentan después queden juntos', () => {
    const rondas = construirCuadro(enJuego);

    // La semifinal 5 recibe a los ganadores de los cuartos 3 y 4: esos cuartos pasan arriba de todo.
    expect(rondas[1].cruces.map(c => c.partido?.id)).toEqual([5, 6]);
    expect(rondas[0].cruces.map(c => c.partido?.id)).toEqual([3, 4, 1, 2]);
    expect(rondas[1].cruces[0].origenes).toEqual([0, 1]);
    expect(rondas[1].cruces[1].origenes).toEqual([2, 3]);
    // Cada cruce queda a media altura de los dos que lo alimentan.
    expect(rondas[1].cruces.map(c => c.centro)).toEqual([0.5, 2.5]);
    expect(rondas[2].cruces[0].centro).toBe(1.5);
  });

  it('sin fixture proyecta el cuadro con las parejas inscriptas', () => {
    const rondas = construirCuadro([], 5);

    expect(rondas.map(r => r.nombre)).toEqual(['Cuartos de Final', 'Semifinal', 'Final']);
    expect(rondas.map(r => r.cruces.length)).toEqual([3, 2, 1]);
    expect(rondas.every(r => !r.generada)).toBeTrue();
    // Con un número impar, el último cruce de la semifinal recibe a una sola pareja.
    expect(rondas[1].cruces[1].origenes).toEqual([2]);
    expect(construirCuadro([], 1)).toEqual([]);
  });

  it('respeta el pase directo y reconoce al campeón', () => {
    const rondas = construirCuadro([
      partido(1, 1, 'Cuartos de Final', 21, 22, [2, 1]),
      partido(2, 1, 'Cuartos de Final', 23, 24, [0, 2]),
      partido(3, 1, 'Cuartos de Final', 25, 26, [2, 1]),
      partido(4, 2, 'Semifinal', 21, 24, [2, 0]),
      partido(5, 2, 'Semifinal', 25, null, null),
      partido(6, 3, 'Final', 21, 25, [1, 2])
    ]);

    expect(rondas[1].cruces[1].partido?.estado).toBe('FINALIZADO');
    expect(rondas[1].cruces[1].origenes).toEqual([2]);
    expect(finalJugada(rondas)?.ganadorInscripcionId).toBe(25);
  });
});

describe('caminoDePareja', () => {
  const rondas = construirCuadro([
    partido(1, 1, 'Cuartos de Final', 11, 14, [2, 1]),
    partido(2, 1, 'Cuartos de Final', 16, 12, [0, 2]),
    partido(3, 1, 'Cuartos de Final', 13, 17, [2, 0]),
    partido(4, 1, 'Cuartos de Final', 18, 15, [1, 2]),
    partido(5, 2, 'Semifinal', 11, 12, null),
    partido(6, 2, 'Semifinal', 13, 15, [2, 1])
  ]);

  it('marca lo jugado y, si sigue en carrera, los casilleros hasta la final', () => {
    const camino = caminoDePareja(rondas, 11);

    expect(camino.enCarrera).toBeTrue();
    expect([...camino.jugados]).toEqual(['p1', 'p5']);
    expect(camino.posibles.size).toBe(1);
  });

  it('una pareja eliminada no tiene camino por delante', () => {
    const camino = caminoDePareja(rondas, 14);

    expect(camino.enCarrera).toBeFalse();
    expect([...camino.jugados]).toEqual(['p1']);
    expect(camino.posibles.size).toBe(0);
  });

  it('sin pareja del usuario no marca nada', () => {
    const camino = caminoDePareja(rondas, null);

    expect(camino.jugados.size).toBe(0);
    expect(camino.enCarrera).toBeFalse();
  });
});
