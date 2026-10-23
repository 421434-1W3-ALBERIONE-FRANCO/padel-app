import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BonoService } from '../../core/services/bono.service';
import { UiCardComponent } from '../../shared/components/ui-card/ui-card.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { FichajeComponent } from '../../shared/components/fichaje/fichaje.component';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, UiCardComponent, NotificationBellComponent, FichajeComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private bonoService = inject(BonoService);

  // Obtener señal del usuario actual y saldo
  currentUser = this.authService.currentUser;
  saldoHoras = this.bonoService.saldoHorasBono;

  ngOnInit() {
    // El admin también puede usar el panel de jugador (doble perfil): no se lo
    // redirige. Por defecto aterriza en /admin desde el login, pero puede alternar.
    // Sin saldo cargado el badge muestra 0 hs: no hace falta interrumpir el panel con un error.
    this.bonoService.cargarMisBonos().subscribe({ error: () => {} });
  }

  logout(): void {
    this.authService.logout();
  }
}
