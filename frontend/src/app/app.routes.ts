import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegistroComponent } from './features/auth/registro/registro.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { CanchasAdminComponent } from './features/admin/canchas/canchas-admin.component';
import { CalendarioComponent } from './features/reservas/calendario/calendario.component';
import { MisReservasComponent } from './features/reservas/mis-reservas/mis-reservas.component';
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
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
