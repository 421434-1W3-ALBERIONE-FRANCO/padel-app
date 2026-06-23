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
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
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
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
