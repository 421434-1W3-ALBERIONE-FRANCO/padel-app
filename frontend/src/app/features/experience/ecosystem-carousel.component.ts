import { Component, EventEmitter, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface OfertaEcosistema {
  titulo: string;
  descripcion: string;
  ruta: string;
}

/**
 * Vitrina tipo e-commerce de las 4 ofertas del ecosistema (reservas, torneos,
 * ranking, comunidad): una imagen a la vez, con avance automático, pensada
 * para ocupar solo el espacio que la paleta 3D deja libre en la sección
 * (no un grid a pantalla completa). El click navega a la sección real;
 * la ruta destino ya está protegida por authGuard, así que si el usuario no
 * está logueado termina en el login, y si lo está entra directo sin importar
 * si es JUGADOR o ADMIN.
 */
@Component({
  selector: 'app-ecosystem-carousel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ecosystem-carousel.component.html',
  styleUrls: ['./ecosystem-carousel.component.css']
})
export class EcosystemCarouselComponent implements OnInit, OnDestroy {
  @Output() navigateTo = new EventEmitter<string>();

  private readonly DELAY_MS = 6500;
  private intervalId?: ReturnType<typeof setInterval>;

  ofertas: OfertaEcosistema[] = [
    {
      titulo: 'Reservá tu cancha',
      descripcion: 'Elegí día y horario con disponibilidad en tiempo real.',
      ruta: '/reservas/calendario'
    },
    {
      titulo: 'Sumate a un torneo',
      descripcion: 'Ligas, torneos y torneos express organizados por el club.',
      ruta: '/torneos'
    },
    {
      titulo: 'Ranking por categoría',
      descripcion: 'Sumá puntos partido a partido y subí en tu categoría.',
      ruta: '/ranking'
    },
    {
      titulo: 'Encontrá con quién jugar',
      descripcion: 'Buscá pareja o completá tu cancha con la comunidad.',
      ruta: '/solicitudes-partido'
    }
  ];

  activeIndex = signal(0);

  ngOnInit() {
    this.scheduleNext();
  }

  ngOnDestroy() {
    if (this.intervalId) clearInterval(this.intervalId);
  }

  private scheduleNext() {
    if (this.intervalId) clearInterval(this.intervalId);
    this.intervalId = setInterval(() => {
      this.activeIndex.set((this.activeIndex() + 1) % this.ofertas.length);
    }, this.DELAY_MS);
  }

  irA(indice: number) {
    this.activeIndex.set(indice);
    this.scheduleNext();
  }

  onSelect(event: Event, ruta: string) {
    event.preventDefault();
    this.navigateTo.emit(ruta);
  }
}
