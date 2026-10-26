import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

// Todas las pantallas se cargan bajo demanda: el bundle inicial ya no incluye la consola de admin ni la escena
// 3D, que la mayoría de los jugadores nunca abre.
const STAFF = { roles: ['ADMIN', 'RECEPCIONISTA'] };

export const routes: Routes = [
  {
    path: 'experience',
    loadComponent: () => import('./features/experience/experience.component').then(m => m.ExperienceComponent)
  },
  {
    path: 'auth/login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'auth/registro',
    loadComponent: () => import('./features/auth/registro/registro.component').then(m => m.RegistroComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'auth/recuperar-password',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'auth/resetear-password',
    loadComponent: () => import('./features/auth/reset-password/reset-password.component').then(m => m.ResetPasswordComponent),
    canActivate: [guestGuard]
  },
  {
    // Destino del enlace del email de registro.
    path: 'auth/verificar-email',
    loadComponent: () => import('./features/auth/verificar-email/verificar-email.component').then(m => m.VerificarEmailComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'perfil/password',
    loadComponent: () => import('./features/perfil/cambiar-password/cambiar-password.component').then(m => m.CambiarPasswordComponent),
    canActivate: [authGuard]
  },
  {
    // Consola de administración: shell con sidebar + pantallas como rutas hijas.
    // El guard del padre protege todo el subárbol (solo ADMIN).
    path: 'admin',
    loadComponent: () => import('./features/admin/layout/admin-layout.component').then(m => m.AdminLayoutComponent),
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] },
    children: [
      { path: '', loadComponent: () => import('./features/admin/overview/admin-overview.component').then(m => m.AdminOverviewComponent) },
      { path: 'reservas', loadComponent: () => import('./features/admin/reservas/reservas-admin.component').then(m => m.ReservasAdminComponent) },
      { path: 'ocupacion', loadComponent: () => import('./features/admin/ocupacion/ocupacion-admin.component').then(m => m.OcupacionAdminComponent) },
      { path: 'staff', loadComponent: () => import('./features/admin/staff/staff-admin.component').then(m => m.StaffAdminComponent) },
      { path: 'canchas', loadComponent: () => import('./features/admin/canchas/canchas-admin.component').then(m => m.CanchasAdminComponent) },
      { path: 'productos', loadComponent: () => import('./features/admin/productos/productos-admin.component').then(m => m.ProductosAdminComponent) },
      { path: 'tipos-bono', loadComponent: () => import('./features/admin/tipos-bono/tipos-bono-admin.component').then(m => m.TiposBonoAdminComponent) },
      { path: 'configuracion', loadComponent: () => import('./features/admin/configuracion/configuracion.component').then(m => m.ConfiguracionComponent) },
      { path: 'comisiones', loadComponent: () => import('./features/admin/comisiones/comisiones-reporte.component').then(m => m.ComisionesReporteComponent) },
      { path: 'control-horario', loadComponent: () => import('./features/admin/control-horario/control-horario.component').then(m => m.ControlHorarioComponent) },
    ]
  },
  {
    path: 'recepcion/carga-consumos',
    loadComponent: () => import('./features/recepcion/carga-consumos/carga-consumos.component').then(m => m.CargaConsumosComponent),
    canActivate: [authGuard],
    data: STAFF
  },
  {
    path: 'recepcion/alta-turno',
    loadComponent: () => import('./features/recepcion/alta-turno/alta-turno.component').then(m => m.AltaTurnoComponent),
    canActivate: [authGuard],
    data: STAFF
  },
  {
    path: 'recepcion/cierre-cuenta/:reservaId',
    loadComponent: () => import('./features/recepcion/cierre-cuenta/cierre-cuenta.component').then(m => m.CierreCuentaComponent),
    canActivate: [authGuard],
    data: STAFF
  },
  {
    path: 'recepcion/caja',
    loadComponent: () => import('./features/recepcion/caja-diaria/caja-diaria.component').then(m => m.CajaDiariaComponent),
    canActivate: [authGuard],
    data: STAFF
  },
  {
    path: 'reservas/calendario',
    loadComponent: () => import('./features/reservas/calendario/calendario.component').then(m => m.CalendarioComponent),
    canActivate: [authGuard]
  },
  {
    path: 'reservas/mis-reservas',
    loadComponent: () => import('./features/reservas/mis-reservas/mis-reservas.component').then(m => m.MisReservasComponent),
    canActivate: [authGuard]
  },
  {
    path: 'bonos/mis-bonos',
    loadComponent: () => import('./features/bonos/mis-bonos/mis-bonos.component').then(m => m.MisBonosComponent),
    canActivate: [authGuard]
  },
  {
    path: 'bonos/comprar',
    loadComponent: () => import('./features/bonos/compra-bono/compra-bono.component').then(m => m.CompraBonoComponent),
    canActivate: [authGuard]
  },
  {
    path: 'pagos/checkout/:reservaId',
    loadComponent: () => import('./features/pagos/checkout/checkout.component').then(m => m.CheckoutComponent),
    canActivate: [authGuard]
  },
  {
    path: 'torneos',
    loadComponent: () => import('./features/torneos/lista-torneos/lista-torneos.component').then(m => m.ListaTorneosComponent),
    canActivate: [authGuard]
  },
  {
    path: 'torneos/:id',
    loadComponent: () => import('./features/torneos/torneo-detalle/torneo-detalle.component').then(m => m.TorneoDetalleComponent),
    canActivate: [authGuard]
  },
  {
    path: 'ranking',
    loadComponent: () => import('./features/ranking/ranking-jugadores/ranking-jugadores.component').then(m => m.RankingJugadoresComponent),
    canActivate: [authGuard]
  },
  {
    path: 'match',
    loadComponent: () => import('./features/match/match/match.component').then(m => m.MatchComponent),
    canActivate: [authGuard]
  },
  {
    path: 'perfil/jugador',
    loadComponent: () => import('./features/match/perfil-jugador/perfil-jugador.component').then(m => m.PerfilJugadorComponent),
    canActivate: [authGuard]
  },
  {
    path: 'solicitudes-partido',
    loadComponent: () => import('./features/solicitudes/tablon-solicitudes/tablon-solicitudes.component').then(m => m.TablonSolicitudesComponent),
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
