import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TablonSolicitudesComponent } from './tablon-solicitudes.component';
import { AuthService } from '../../../core/services/auth.service';
import { CanchaAdminService } from '../../../core/services/cancha-admin.service';
import { SolicitudPartidoService } from '../../../core/services/solicitud-partido.service';
import { PostulacionSolicitudResponse, SolicitudPartidoResponse } from '../../../shared/models/solicitud-partido.model';
import { UsuarioResponse } from '../../../shared/models/usuario.model';

const lucia: UsuarioResponse = {
  id: 3, nombre: 'Lucía', apellido: 'Fernández', email: 'lucia@test.com', telefono: null, rol: 'JUGADOR', activo: true,
  emailVerificado: true
};

function postulacion(id: number, solicitudId: number, jugadorId: number, jugadorNombre: string): PostulacionSolicitudResponse {
  return { id, solicitudId, jugadorId, jugadorNombre, contacto: null, mensaje: null, estado: 'PENDIENTE', createdAt: '2026-10-18T10:00:00' };
}

function solicitud(id: number, creadorId: number, creadorNombre: string, postulaciones: PostulacionSolicitudResponse[]): SolicitudPartidoResponse {
  return {
    id, creadorId, creadorNombre, creadorContacto: null, tipoSolicitud: 'BUSCA_JUGADORES', categoria: 'CUARTA',
    cantidadJugadoresFaltantes: 2, fechaHoraPropuesta: '2026-11-20T19:00:00', canchaId: null, canchaNombre: null,
    descripcion: null, estado: 'ABIERTA', createdAt: '2026-10-18T09:00:00', postulaciones,
    cantidadPostulaciones: postulaciones.length
  };
}

describe('TablonSolicitudesComponent', () => {
  let respuesta: SolicitudPartidoResponse[] = [];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TablonSolicitudesComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: signal(lucia) } },
        { provide: CanchaAdminService, useValue: { getCanchas: () => of([]) } },
        { provide: SolicitudPartidoService, useValue: { buscar: () => of(respuesta) } }
      ]
    });
  });

  function enlaces(fixture: { nativeElement: HTMLElement }, prefijo: string): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll<HTMLAnchorElement>(`a[href^="${prefijo}"]`)).map(a => a.getAttribute('href') ?? '');
  }

  it('reconoce por id la solicitud propia y la postulación hecha', () => {
    const deJuan = solicitud(1, 2, 'Juan Jugador', [postulacion(10, 1, 3, 'Lucía Fernández')]);
    const deLucia = solicitud(2, 3, 'Lucía Fernández', [postulacion(11, 2, 201, 'Martina López')]);
    respuesta = [deJuan, deLucia];
    const fixture = TestBed.createComponent(TablonSolicitudesComponent);
    fixture.detectChanges();
    const tablon = fixture.componentInstance;

    expect(tablon.esCreador(deJuan)).toBeFalse();
    expect(tablon.yaMePostule(deJuan)).toBeTrue();
    expect(tablon.esCreador(deLucia)).toBeTrue();
    expect(tablon.yaMePostule(deLucia)).toBeFalse();

    // Puede cancelar solo la suya y no se le ofrece postularse a ninguna de las dos.
    const botones: string[] = Array.from(fixture.nativeElement.querySelectorAll('button'))
      .map(b => (b as HTMLElement).textContent?.trim() ?? '');
    expect(botones.filter(b => b === 'Cancelar').length).toBe(1);
    expect(botones).not.toContain('Postularme');
    expect(fixture.nativeElement.textContent).toContain('Ya te postulaste a esta solicitud.');
    expect(enlaces(fixture, 'mailto:')).toEqual([]);
  });

  it('a una aceptada le muestra email y WhatsApp de los otros jugadores del partido', () => {
    // Como lo arma el backend para Lucía, aceptada: el contacto del creador y de Martina (aceptada); la suya va sin contacto.
    const partido: SolicitudPartidoResponse = {
      ...solicitud(3, 2, 'Juan Jugador', [
        { ...postulacion(20, 3, 3, 'Lucía Fernández'), estado: 'ACEPTADA' },
        { ...postulacion(21, 3, 201, 'Martina López'), estado: 'ACEPTADA', contacto: { email: 'martina@demo.local', telefono: '351 555-0201' } }
      ]),
      creadorContacto: { email: 'juan@test.com', telefono: '3515550002' },
      cantidadPostulaciones: 3
    };
    respuesta = [partido];
    const fixture = TestBed.createComponent(TablonSolicitudesComponent);
    fixture.detectChanges();
    fixture.componentInstance.toggleExpandido(3);
    fixture.detectChanges();

    expect(fixture.componentInstance.meAceptaron(partido)).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Te aceptaron: ya podés coordinar el partido');
    expect(fixture.nativeElement.textContent).toContain('Ves a los jugadores aceptados del partido');
    expect(enlaces(fixture, 'mailto:')).toEqual(['mailto:juan@test.com', 'mailto:martina@demo.local']);
    const mensaje = (nombre: string) =>
      encodeURIComponent(`¡Hola ${nombre}! Te escribo por el partido del 20/11 a las 19:00 que armamos en el tablón de PadelApp.`);
    expect(enlaces(fixture, 'https://wa.me/')).toEqual([
      `https://wa.me/543515550002?text=${mensaje('Juan')}`,
      `https://wa.me/543515550201?text=${mensaje('Martina')}`
    ]);
  });

  it('con la solicitud cancelada no da por aceptada a nadie ni muestra contactos', () => {
    const cancelada: SolicitudPartidoResponse = {
      ...solicitud(4, 2, 'Juan Jugador', [{ ...postulacion(30, 4, 3, 'Lucía Fernández'), estado: 'ACEPTADA' }]),
      estado: 'CANCELADA'
    };
    respuesta = [cancelada];
    const fixture = TestBed.createComponent(TablonSolicitudesComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.meAceptaron(cancelada)).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Ya te postulaste a esta solicitud.');
    expect(enlaces(fixture, 'mailto:')).toEqual([]);
    expect(enlaces(fixture, 'https://wa.me/')).toEqual([]);
  });
});
