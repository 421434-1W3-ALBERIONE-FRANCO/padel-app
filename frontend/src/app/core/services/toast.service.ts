import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

/**
 * Servicio global de avisos (toasts). Criterio unificado de auto-cierre:
 * cada toast desaparece solo tras unos segundos, o al hacer clic en él.
 * Los errores viven un poco más que los éxitos.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private seq = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  private push(type: ToastType, message: string | null | undefined, durationMs: number): void {
    if (!message) return; // ignora null/undefined/'' — sirve como no-op seguro
    // Evita el mismo aviso repetido en pantalla (ej. la sesión vencida que informan a la vez el
    // interceptor y la pantalla, o varios pedidos en paralelo que fallan con el mismo mensaje).
    if (this._toasts().some(t => t.message === message)) return;
    const id = ++this.seq;
    this._toasts.update(list => [...list, { id, type, message }]);
    if (durationMs > 0) {
      this.timers.set(id, setTimeout(() => this.dismiss(id), durationMs));
    }
  }

  success(message: string | null | undefined, durationMs = 4000): void {
    this.push('success', message, durationMs);
  }

  error(message: string | null | undefined, durationMs = 6000): void {
    this.push('error', message, durationMs);
  }

  info(message: string | null | undefined, durationMs = 4000): void {
    this.push('info', message, durationMs);
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this._toasts.update(list => list.filter(t => t.id !== id));
  }
}
