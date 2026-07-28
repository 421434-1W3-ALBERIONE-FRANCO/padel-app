import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BonoService } from '../../core/services/bono.service';
import { UiCardComponent } from '../../shared/components/ui-card/ui-card.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, UiCardComponent, NotificationBellComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private bonoService = inject(BonoService);
  private router = inject(Router);

  // Obtener señal del usuario actual y saldo
  currentUser = this.authService.currentUser;
  saldoHoras = this.bonoService.saldoHorasBono;

  ngOnInit() {
    // El admin no usa el panel de jugador: se lo redirige a su consola.
    if (this.authService.userRole() === 'ADMIN') {
      this.router.navigate(['/admin']);
      return;
    }

    this.bonoService.cargarMisBonos().subscribe({
      error: () => console.warn('Could not load user coupon packs balance.')
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
