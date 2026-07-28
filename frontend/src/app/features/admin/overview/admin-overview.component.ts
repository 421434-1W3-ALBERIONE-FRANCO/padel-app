import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

interface QuickCard {
  label: string;
  desc: string;
  route: string;
  icon: string;
  external?: boolean;
  disabled?: boolean;
}

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-overview.component.html'
})
export class AdminOverviewComponent {
  private authService = inject(AuthService);
  currentUser = this.authService.currentUser;

  readonly cards: QuickCard[] = [
    { label: 'Canchas y Franjas', desc: 'Alta, baja y edición de canchas, horarios y bloqueos.', route: '/admin/canchas', icon: 'M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18' },
    { label: 'Productos y Stock', desc: 'Catálogo de la tienda y control de inventario.', route: '/admin/productos', icon: 'M20.25 7.5 12 12m0 0L3.75 7.5M12 12v9' },
    { label: 'Control Horario', desc: 'Turnos, sesiones de trabajo y liquidación del staff.', route: '/admin/control-horario', icon: 'M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z' },
    { label: 'Comisiones', desc: 'Reporte de comisiones generadas por el personal.', route: '/admin/comisiones', icon: 'M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75Z' },
    { label: 'Configuración', desc: 'Parámetros globales del sistema.', route: '/admin/configuracion', icon: 'M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z' },
    { label: 'Reservas globales', desc: 'Ver y cancelar cualquier reserva. (Próximamente)', route: '/admin/reservas', icon: 'M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25', disabled: true },
  ];
}
