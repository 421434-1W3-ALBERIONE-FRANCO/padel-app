import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Contenedor global de toasts. Se renderiza una sola vez en el root.
 * Cada toast se auto-cierra (timer en el servicio) y también se cierra al clic.
 */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed top-4 right-4 z-[200] flex flex-col gap-2 w-[calc(100vw-2rem)] max-w-sm pointer-events-none">
      <div
        *ngFor="let toast of toastService.toasts()"
        (click)="toastService.dismiss(toast.id)"
        class="pointer-events-auto cursor-pointer rounded-xl border px-4 py-3 text-sm shadow-lg backdrop-blur-md flex items-start gap-2.5 animate-[toastIn_0.2s_ease-out]"
        [ngClass]="{
          'bg-primary-950/80 border-primary-700/50 text-primary-100': toast.type === 'success',
          'bg-red-950/80 border-red-800/50 text-red-100': toast.type === 'error',
          'bg-slate-900/85 border-white/10 text-slate-100': toast.type === 'info'
        }">
        <svg class="w-4.5 h-4.5 shrink-0 mt-0.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path *ngIf="toast.type === 'success'" stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5"/>
          <path *ngIf="toast.type === 'error'" stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z"/>
          <path *ngIf="toast.type === 'info'" stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 0 1 1.063.852l-.708 2.836a.75.75 0 0 0 1.063.853l.041-.021M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-3.75h.008v.008H12V8.25Z"/>
        </svg>
        <span class="flex-1 leading-snug">{{ toast.message }}</span>
      </div>
    </div>
  `,
  styles: [`
    @keyframes toastIn {
      from { opacity: 0; transform: translateY(-8px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class ToastContainerComponent {
  toastService = inject(ToastService);
}
