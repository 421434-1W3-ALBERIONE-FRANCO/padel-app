import { Component, effect, inject, input, signal } from '@angular/core';
import { FotoJugadorService } from '../../../core/services/foto-jugador.service';

/**
 * Avatar redondo de un jugador: su foto de perfil si tiene, o sus iniciales. Ocupa todo el contenedor
 * (el aro de color lo pone quien lo usa).
 */
@Component({
  selector: 'app-avatar-jugador',
  host: { class: 'block w-full h-full' },
  template: `
    @if (src() || url(); as foto) {
      <img [src]="foto" [alt]="alt()" class="w-full h-full rounded-full object-cover bg-surface-1" draggable="false" />
    } @else {
      <div class="w-full h-full rounded-full bg-surface-1 flex items-center justify-center font-extrabold font-display text-slate-100 select-none"
           [class]="claseTexto()" [attr.aria-label]="alt() || null">
        {{ iniciales() }}
      </div>
    }
  `
})
export class AvatarJugadorComponent {
  private fotos = inject(FotoJugadorService);

  usuarioId = input<number | null | undefined>(null);
  fotoVersion = input<number | null | undefined>(null);
  iniciales = input('');
  alt = input('');
  claseTexto = input('text-xl');
  /** Una imagen local (por ejemplo, la foto recién elegida que todavía no se subió). Tiene prioridad. */
  src = input<string | null>(null);

  url = signal<string | null>(null);

  constructor() {
    effect(onCleanup => {
      const id = this.usuarioId();
      const version = this.fotoVersion();
      this.url.set(null);
      if (id == null || version == null) return;
      const sub = this.fotos.url(id, version).subscribe(url => this.url.set(url));
      onCleanup(() => sub.unsubscribe());
    });
  }
}
