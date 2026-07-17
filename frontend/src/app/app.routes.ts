import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegistroComponent } from './features/auth/registro/registro.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { CanchasAdminComponent } from './features/admin/canchas/canchas-admin.component';
import { CalendarioComponent } from './features/reservas/calendario/calendario.component';
import { MisReservasComponent } from './features/reservas/mis-reservas/mis-reservas.component';
import { CheckoutComponent } from './features/pagos/checkout/checkout.component';
import { CompraBonoComponent } from './features/bonos/compra-bono/compra-bono.component';
import { MisBonosComponent } from './features/bonos/mis-bonos/mis-bonos.component';
import { ProductosAdminComponent } from './features/admin/productos/productos-admin.component';
import { CargaConsumosComponent } from './features/recepcion/carga-consumos/carga-consumos.component';
import { CierreCuentaComponent } from './features/recepcion/cierre-cuenta/cierre-cuenta.component';
import { ExperienceComponent } from './features/experience/experience.component';
import { ListaTorneosComponent } from './features/torneos/lista-torneos/lista-torneos.component';
import { TorneoDetalleComponent } from './features/torneos/torneo-detalle/torneo-detalle.component';
import { RankingJugadoresComponent } from './features/ranking/ranking-jugadores/ranking-jugadores.component';
import { TablonSolicitudesComponent } from './features/solicitudes/tablon-solicitudes/tablon-solicitudes.component';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'experience',
    component: ExperienceComponent
  },
  {
    path: 'auth/login',
    component: LoginComponent,
    canActivate: [guestGuard]
  },
  {
    path: 'auth/registro',
    component: RegistroComponent,
    canActivate: [guestGuard]
  },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  {
    path: 'admin/canchas',
    component: CanchasAdminComponent,
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/productos',
    component: ProductosAdminComponent,
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'recepcion/carga-consumos',
    component: CargaConsumosComponent,
    canActivate: [authGuard],
    data: { roles: ['ADMIN', 'RECEPCIONISTA'] }
  },
  {
    path: 'recepcion/cierre-cuenta/:reservaId',
    component: CierreCuentaComponent,
    canActivate: [authGuard],
    data: { roles: ['ADMIN', 'RECEPCIONISTA'] }
  },
  {
    path: 'reservas/calendario',
    component: CalendarioComponent,
    canActivate: [authGuard]
  },
  {
    path: 'reservas/mis-reservas',
    component: MisReservasComponent,
    canActivate: [authGuard]
  },
  {
    path: 'bonos/mis-bonos',
    component: MisBonosComponent,
    canActivate: [authGuard]
  },
  {
    path: 'bonos/comprar',
    component: CompraBonoComponent,
    canActivate: [authGuard]
  },
  {
    path: 'pagos/checkout/:reservaId',
    component: CheckoutComponent,
    canActivate: [authGuard]
  },
  {
    path: 'torneos',
    component: ListaTorneosComponent,
    canActivate: [authGuard]
  },
  {
    path: 'torneos/:id',
    component: TorneoDetalleComponent,
    canActivate: [authGuard]
  },
  {
    path: 'ranking',
    component: RankingJugadoresComponent,
    canActivate: [authGuard]
  },
  {
    path: 'solicitudes-partido',
    component: TablonSolicitudesComponent,
    canActivate: [authGuard]
  },
  {
    path: '',
    redirectTo: 'experience',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'experience'
  }
];
