import { HttpInterceptorFn, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { MOCK_API_ENABLED } from './mock-config';
import { environment } from '../../../environments/environment';

const BASE = environment.apiUrl;
const DIAS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const STORAGE_KEY = '__mock_db__';

function nowIso() { return new Date().toISOString(); }

// Espeja ReservaServiceImpl.calcularFechaMaximaReservable: mes en curso, o el
// siguiente completo si estamos en la última semana del mes actual.
function fechaMaximaReservable(): string {
  const hoy = new Date();
  const ultimoDiaMesActual = new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0).getDate();
  const diasParaFinDeMes = ultimoDiaMesActual - hoy.getDate();
  const mesLimite = diasParaFinDeMes <= 6 ? hoy.getMonth() + 1 : hoy.getMonth();
  const fin = new Date(hoy.getFullYear(), mesLimite + 1, 0);
  return fin.toISOString().substring(0, 10);
}

function seed() {
  return {
    nextId: { cancha: 3, franja: 3, bloqueo: 1, reserva: 1, bono: 2, producto: 4, usuario: 4, torneo: 1, inscripcionTorneo: 1, partidoTorneo: 1, ranking: 1, solicitud: 1, postulacion: 1 },
    usuarios: [
      { id: 1, nombre: 'Admin', apellido: 'Dev', email: 'admin@test.com', telefono: '', rol: 'ADMIN', activo: true, password: 'admin123' },
      { id: 2, nombre: 'Juan', apellido: 'Jugador', email: 'juan@test.com', telefono: '', rol: 'JUGADOR', activo: true, password: 'test123' },
      { id: 3, nombre: 'Lucía', apellido: 'Fernández', email: 'lucia@test.com', telefono: '', rol: 'JUGADOR', activo: true, password: 'test123' }
    ],
    torneos: [] as any[],
    inscripcionesTorneo: [] as any[],
    partidosTorneo: [] as any[],
    rankingJugadores: [] as any[],
    solicitudesPartido: [] as any[],
    canchas: [
      { id: 1, nombre: 'Cancha Central', tipoSuelo: 'BLINDEX', techada: true, tieneLuz: true, activa: true },
      { id: 2, nombre: 'Cancha 2', tipoSuelo: 'SINTETICO', techada: false, tieneLuz: true, activa: true }
    ],
    franjas: [
      { id: 1, canchaId: 1, horaInicio: '08:00', horaFin: '09:30', duracionMin: 90, precioBase: 1000, precioNocturno: 1200, diasAplicables: [...DIAS], createdAt: nowIso(), updatedAt: nowIso() },
      { id: 2, canchaId: 1, horaInicio: '18:00', horaFin: '19:30', duracionMin: 90, precioBase: 1500, precioNocturno: 1800, diasAplicables: [...DIAS], createdAt: nowIso(), updatedAt: nowIso() }
    ],
    bloqueos: [] as any[],
    reservas: [] as any[],
    bonos: [
      { id: 1, usuarioId: 2, usuarioEmail: 'juan@test.com', tipo: 'PACK 10HS', horasTotales: 10, horasUsadas: 2, precioPagado: 8000, fechaVencimiento: '2026-12-31', estado: 'ACTIVO', createdAt: nowIso(), updatedAt: null }
    ],
    productos: [
      { id: 1, nombre: 'Coca Cola', categoria: 'BEBIDA', precio: 1500, stock: 40, activo: true },
      { id: 2, nombre: 'Agua', categoria: 'BEBIDA', precio: 1000, stock: 60, activo: true },
      { id: 3, nombre: 'Paletas Alquiler', categoria: 'ALQUILER', precio: 2000, stock: 10, activo: true }
    ],
    consumos: [] as any[]
  };
}

function loadDb() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    // Merge sobre seed() para que las colecciones y contadores agregados en versiones nuevas
    // del mock (ej. torneos) existan aunque el usuario tenga una sesión vieja en localStorage.
    if (raw) {
      const parsed = JSON.parse(raw);
      return { ...seed(), ...parsed, nextId: { ...seed().nextId, ...parsed.nextId } };
    }
  } catch { /* ignore corrupt state, reseed */ }
  return seed();
}

const db = loadDb();

function persist() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(db)); } catch { /* storage unavailable, mock stays in-memory only */ }
}

function decodeAuth(req: any): { email: string; rol: string } | null {
  const header = req.headers.get('Authorization');
  if (!header) return null;
  try { return JSON.parse(atob(header.replace('Bearer ', ''))); } catch { return null; }
}

function ok(body: any, status = 200) {
  return of(new HttpResponse({ status, body })).pipe(delay(150));
}
function err(status: number, message: string) {
  return throwError(() => new HttpErrorResponse({ status, error: { status, message } })).pipe(delay(150));
}
function requireRol(auth: { rol: string } | null, roles: string[]) {
  if (!auth) return err(401, 'No autenticado');
  if (!roles.includes(auth.rol)) return err(403, 'No tiene permisos para acceder a este recurso');
  return null;
}

export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MOCK_API_ENABLED || !req.url.startsWith(BASE)) return next(req);

  const path = req.url.substring(BASE.length);
  const auth = decodeAuth(req);
  const segs = path.split('/').filter(Boolean);

  // ---- AUTH ----
  if (path === '/auth/login' && req.method === 'POST') {
    const { email, password } = req.body as any;
    const u = db.usuarios.find((x: any) => x.email === email && x.password === password);
    if (!u) return err(401, 'Credenciales inválidas');
    const token = btoa(JSON.stringify({ email: u.email, rol: u.rol }));
    return ok({ token, usuario: { id: u.id, nombre: u.nombre, apellido: u.apellido, email: u.email, telefono: u.telefono, rol: u.rol, activo: u.activo } });
  }
  if (path === '/auth/registro' && req.method === 'POST') {
    const body = req.body as any;
    if (db.usuarios.some((x: any) => x.email === body.email)) return err(409, `El correo electrónico ${body.email} ya está registrado.`);
    const u = { id: db.nextId.usuario++, nombre: body.nombre, apellido: body.apellido, email: body.email, telefono: body.telefono ?? '', rol: 'JUGADOR', activo: true, password: body.password };
    db.usuarios.push(u); persist();
    return ok({ id: u.id, nombre: u.nombre, apellido: u.apellido, email: u.email, telefono: u.telefono, rol: u.rol, activo: u.activo }, 201);
  }

  // ---- CANCHAS ----
  if (segs[0] === 'canchas' && segs.length === 1 && req.method === 'GET') return ok(db.canchas.filter((c: any) => c.activa));
  if (segs[0] === 'canchas' && segs.length === 1 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const c = { id: db.nextId.cancha++, ...(req.body as any), activa: true };
    db.canchas.push(c); persist();
    return ok(c, 201);
  }
  if (segs[0] === 'canchas' && segs.length === 2 && req.method === 'GET' && !isNaN(+segs[1])) {
    const c = db.canchas.find((x: any) => x.id === +segs[1]);
    return c ? ok(c) : err(404, 'Cancha no encontrada');
  }
  if (segs[0] === 'canchas' && segs.length === 2 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const c = db.canchas.find((x: any) => x.id === +segs[1]);
    if (!c) return err(404, 'Cancha no encontrada');
    Object.assign(c, req.body as any); persist();
    return ok(c);
  }
  if (segs[0] === 'canchas' && segs.length === 2 && req.method === 'DELETE') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const c = db.canchas.find((x: any) => x.id === +segs[1]);
    if (!c) return err(404, 'Cancha no encontrada');
    c.activa = false; persist();
    return ok(null, 204);
  }

  // ---- FRANJAS ----
  if (segs[0] === 'canchas' && segs[2] === 'franjas' && segs.length === 3 && req.method === 'GET') {
    return ok(db.franjas.filter((f: any) => f.canchaId === +segs[1]));
  }
  if (segs[0] === 'canchas' && segs[2] === 'franjas' && segs.length === 3 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const f = { id: db.nextId.franja++, canchaId: +segs[1], ...(req.body as any), duracionMin: 90, createdAt: nowIso(), updatedAt: nowIso() };
    db.franjas.push(f); persist();
    return ok(f, 201);
  }
  if (segs[0] === 'franjas-horarias' && segs.length === 2 && req.method === 'GET') {
    const f = db.franjas.find((x: any) => x.id === +segs[1]);
    return f ? ok(f) : err(404, 'Franja horaria no encontrada');
  }
  if (segs[0] === 'franjas-horarias' && segs.length === 2 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const f = db.franjas.find((x: any) => x.id === +segs[1]);
    if (!f) return err(404, 'Franja horaria no encontrada');
    Object.assign(f, req.body as any, { updatedAt: nowIso() }); persist();
    return ok(f);
  }
  if (segs[0] === 'franjas-horarias' && segs.length === 2 && req.method === 'DELETE') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const idx = db.franjas.findIndex((x: any) => x.id === +segs[1]);
    if (idx === -1) return err(404, 'Franja horaria no encontrada');
    db.franjas.splice(idx, 1); persist();
    return ok(null, 204);
  }

  // ---- BLOQUEOS ----
  if (segs[0] === 'canchas' && segs[2] === 'bloqueos' && segs.length === 3 && req.method === 'GET') {
    return ok(db.bloqueos.filter((b: any) => b.canchaId === +segs[1]));
  }
  if (segs[0] === 'canchas' && segs[2] === 'bloqueos' && segs.length === 3 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const b = { id: db.nextId.bloqueo++, canchaId: +segs[1], ...(req.body as any), createdAt: nowIso(), updatedAt: nowIso() };
    db.bloqueos.push(b); persist();
    return ok(b, 201);
  }
  if (segs[0] === 'bloqueos' && segs.length === 2 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const b = db.bloqueos.find((x: any) => x.id === +segs[1]);
    if (!b) return err(404, 'Bloqueo no encontrado');
    Object.assign(b, req.body as any, { updatedAt: nowIso() }); persist();
    return ok(b);
  }
  if (segs[0] === 'bloqueos' && segs.length === 2 && req.method === 'DELETE') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const idx = db.bloqueos.findIndex((x: any) => x.id === +segs[1]);
    if (idx === -1) return err(404, 'Bloqueo no encontrado');
    db.bloqueos.splice(idx, 1); persist();
    return ok(null, 204);
  }

  // ---- DISPONIBILIDAD ----
  if (path.startsWith('/disponibilidad') && req.method === 'GET') {
    const fecha = req.params.get('fecha') as string;
    const canchaId = +(req.params.get('cancha') as string);
    const cancha = db.canchas.find((c: any) => c.id === canchaId);
    const dow = DIAS[new Date(fecha + 'T00:00:00').getDay()];
    const now = new Date();
    const slots = db.franjas.filter((f: any) => f.canchaId === canchaId && f.diasAplicables.includes(dow)).map((f: any) => {
      const ocupado = db.reservas.some((r: any) => r.canchaId === canchaId && r.franjaId === f.id && r.fecha === fecha && r.estadoReserva !== 'CANCELADA');
      const bloqueado = db.bloqueos.some((b: any) => b.canchaId === canchaId && fecha >= b.fechaDesde && fecha <= b.fechaHasta);
      const pasado = new Date(fecha + 'T' + f.horaInicio + ':00') < now;
      return {
        franjaId: f.id, horaInicio: f.horaInicio + ':00', horaFin: f.horaFin + ':00', duracionMin: f.duracionMin,
        precio: f.precioBase, disponible: !ocupado && !bloqueado && !pasado,
        motivoBloqueo: pasado ? 'Horario pasado' : bloqueado ? 'Bloqueado por mantenimiento' : ocupado ? 'Reservado' : null
      };
    });
    return ok({ canchaId, nombreCancha: cancha?.nombre ?? '', fecha, slots });
  }

  // ---- RESERVAS ----
  if (path === '/reservas/mis-reservas' && req.method === 'GET') {
    if (!auth) return err(401, 'No autenticado');
    return ok(db.reservas.filter((r: any) => r.usuarioEmail === auth.email).sort((a: any, b: any) => b.id - a.id));
  }
  if (path === '/reservas' && req.method === 'GET') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    return ok(db.reservas);
  }
  if (path === '/reservas' && req.method === 'POST') {
    if (!auth) return err(401, 'No autenticado');
    const body = req.body as any;
    const cancha = db.canchas.find((c: any) => c.id === body.canchaId);
    const franja = db.franjas.find((f: any) => f.id === body.franjaId);
    if (!cancha || !franja) return err(404, 'Cancha o franja no encontrada');
    if (body.fecha > fechaMaximaReservable()) return err(400, 'Las reservas solo se habilitan dentro del mes en curso (o el siguiente, en la última semana del mes)');
    const ocupado = db.reservas.some((r: any) => r.canchaId === body.canchaId && r.franjaId === body.franjaId && r.fecha === body.fecha && r.estadoReserva !== 'CANCELADA');
    if (ocupado) return err(400, 'El horario seleccionado ya no está disponible');
    const usuario = db.usuarios.find((u: any) => u.email === auth.email)!;
    // Espeja ReservaServiceImpl: el origen es un dato de auditoría, un JUGADOR nunca puede falsearlo.
    const origen = usuario.rol === 'JUGADOR' ? 'APP' : (body.origen ?? 'APP');
    const r = {
      id: db.nextId.reserva++, usuarioId: usuario.id, usuarioEmail: usuario.email, canchaId: cancha.id, canchaNombre: cancha.nombre,
      franjaId: franja.id, fecha: body.fecha, horaInicio: franja.horaInicio + ':00', horaFin: franja.horaFin + ':00',
      precioTotal: franja.precioBase, estadoReserva: 'PENDIENTE_PAGO', origen,
      motivoCancelacion: null, createdAt: nowIso(), updatedAt: null
    };
    db.reservas.push(r); persist();
    return ok(r, 201);
  }
  if (segs[0] === 'reservas' && segs[2] === 'cancelar' && req.method === 'PUT') {
    const r = db.reservas.find((x: any) => x.id === +segs[1]);
    if (!r) return err(404, 'Reserva no encontrada');
    if (r.estadoReserva === 'CANCELADA' || r.estadoReserva === 'COMPLETADA') return err(409, 'La reserva ya no se puede modificar');
    r.estadoReserva = 'CANCELADA';
    r.motivoCancelacion = (req.params.get('motivo') as string) || 'Cancelada por el usuario';
    r.updatedAt = nowIso(); persist();
    return ok(r);
  }
  if (segs[0] === 'reservas' && segs[2] === 'usar-bono' && req.method === 'POST') {
    const r = db.reservas.find((x: any) => x.id === +segs[1]);
    if (!r) return err(404, 'Reserva no encontrada');
    const bono = db.bonos.find((b: any) => b.usuarioEmail === auth?.email && b.estado === 'ACTIVO');
    if (!bono || (bono.horasTotales - bono.horasUsadas) < 2) return err(400, 'No tenés saldo de bono suficiente');
    bono.horasUsadas += 2;
    r.estadoReserva = 'CONFIRMADA'; persist();
    return ok({ id: 1, reservaId: r.id, usuarioId: r.usuarioId, usuarioEmail: r.usuarioEmail, monto: r.precioTotal, metodo: 'BONO', estado: 'APROBADO', mpPreferenceId: null, mpPaymentId: null, createdAt: nowIso(), updatedAt: nowIso() });
  }
  if (segs[0] === 'reservas' && segs.length === 2 && req.method === 'GET') {
    const r = db.reservas.find((x: any) => x.id === +segs[1]);
    return r ? ok(r) : err(404, 'Reserva no encontrada');
  }

  // ---- BONOS ----
  if (path === '/bonos/mis-bonos' && req.method === 'GET') {
    if (!auth) return err(401, 'No autenticado');
    return ok(db.bonos.filter((b: any) => b.usuarioEmail === auth.email));
  }
  if (path === '/bonos' && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const body = req.body as any;
    const usuario = db.usuarios.find((u: any) => u.email === body.usuarioEmail);
    const b = { id: db.nextId.bono++, usuarioId: usuario?.id ?? 0, usuarioEmail: body.usuarioEmail, tipo: body.tipo, horasTotales: body.horasTotales, horasUsadas: 0, precioPagado: body.precioPagado, fechaVencimiento: body.fechaVencimiento, estado: 'ACTIVO', createdAt: nowIso(), updatedAt: null };
    db.bonos.push(b); persist();
    return ok(b, 201);
  }
  if (segs[0] === 'bonos' && segs.length === 2 && req.method === 'GET') {
    const b = db.bonos.find((x: any) => x.id === +segs[1]);
    return b ? ok(b) : err(404, 'Bono no encontrado');
  }

  // ---- PRODUCTOS ----
  if (path === '/productos' && req.method === 'GET') return ok(db.productos.filter((p: any) => p.activo));
  if (path === '/productos' && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const p = { id: db.nextId.producto++, ...(req.body as any) };
    db.productos.push(p); persist();
    return ok(p, 201);
  }
  if (segs[0] === 'productos' && segs.length === 2 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const p = db.productos.find((x: any) => x.id === +segs[1]);
    if (!p) return err(404, 'Producto no encontrado');
    Object.assign(p, req.body as any); persist();
    return ok(p);
  }
  if (segs[0] === 'productos' && segs.length === 2 && req.method === 'DELETE') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const p = db.productos.find((x: any) => x.id === +segs[1]);
    if (!p) return err(404, 'Producto no encontrado');
    p.activo = false; persist();
    return ok(null, 204);
  }

  // ---- CONSUMOS / CIERRE DE CUENTA ----
  if (segs[0] === 'reservas' && segs[2] === 'consumos' && req.method === 'GET') {
    return ok(db.consumos.filter((c: any) => c.reservaId === +segs[1]));
  }
  if (segs[0] === 'reservas' && segs[2] === 'consumos' && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const body = req.body as any;
    const productoId = Number(body.productoId);
    const reservaId = +segs[1];
    const producto = db.productos.find((p: any) => p.id === productoId);
    if (!producto) return err(404, 'Producto no encontrado con ID: ' + body.productoId);
    if (producto.stock < body.cantidad) return err(400, `Stock insuficiente para el producto: ${producto.nombre}. Stock disponible: ${producto.stock}`);

    producto.stock -= body.cantidad;

    // Si ya hay una fila pendiente para este producto en el tab, acumular cantidad en vez de duplicar fila
    const existente = db.consumos.find((c: any) => c.reservaId === reservaId && c.productoId === productoId && c.estadoPago === 'PENDIENTE');
    let c;
    if (existente) {
      existente.cantidad += body.cantidad;
      existente.subtotal = producto.precio * existente.cantidad;
      c = existente;
    } else {
      c = { id: db.consumos.length + 1, reservaId, usuarioId: 0, usuarioEmail: auth!.email, productoId, productoNombre: producto.nombre, pagoId: null, cantidad: body.cantidad, precioUnitario: producto.precio, subtotal: producto.precio * body.cantidad, estadoPago: 'PENDIENTE', createdAt: nowIso() };
      db.consumos.push(c);
    }
    persist();
    return ok(c, 201);
  }
  if (segs[0] === 'reservas' && segs[2] === 'cerrar-cuenta' && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const metodo = (req.body as any).metodo;
    const cobroPresencialInmediato = metodo === 'EFECTIVO' || metodo === 'TARJETA';
    if (cobroPresencialInmediato) {
      db.consumos.filter((c: any) => c.reservaId === +segs[1] && c.estadoPago === 'PENDIENTE').forEach((c: any) => c.estadoPago = 'PAGADO');
      persist();
    }
    return ok(null);
  }

  // ---- REPORTES ----
  if (path === '/reportes/caja-diaria' && req.method === 'GET') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    return ok({ detalles: [{ metodo: 'EFECTIVO', total: 5000 }, { metodo: 'MERCADOPAGO', total: 3200 }] });
  }

  // ---- PAGOS ----
  if (path === '/pagos/preferencias' && req.method === 'POST') {
    const reservaId = (req.body as any).reservaId;
    const reserva = db.reservas.find((r: any) => r.id === reservaId);
    if (!reserva) return err(404, 'Reserva no encontrada con ID: ' + reservaId);
    if (reserva.estadoReserva !== 'PENDIENTE_PAGO') return err(422, `No se puede generar un cobro para una reserva en estado ${reserva.estadoReserva}`);
    return ok({ preferenceId: 'mock-pref-123', initPoint: 'https://mock.mercadopago.com/checkout/mock-pref-123' });
  }
  if (segs[0] === 'pagos' && segs.length === 2 && req.method === 'GET') {
    return ok({ id: +segs[1], reservaId: 1, usuarioId: 2, usuarioEmail: auth?.email ?? '', monto: 1000, metodo: 'MERCADOPAGO', estado: 'PENDIENTE', mpPreferenceId: 'mock-pref-123', mpPaymentId: null, createdAt: nowIso(), updatedAt: null });
  }

  // ---- TORNEOS ----
  function nombreCompleto(u: any) { return `${u.nombre} ${u.apellido}`; }
  function usuarioPorEmail(email: string) { return db.usuarios.find((u: any) => u.email === email); }
  function parejasInscriptas(torneoId: number) {
    return db.inscripcionesTorneo.filter((i: any) => i.torneoId === torneoId && i.estado === 'CONFIRMADA').length;
  }
  function torneoConCupo(t: any) { return { ...t, parejasInscriptas: parejasInscriptas(t.id) }; }
  function nombreRondaEliminacion(cantidad: number): string {
    if (cantidad <= 2) return 'Final';
    if (cantidad <= 4) return 'Semifinal';
    if (cantidad <= 8) return 'Cuartos de Final';
    if (cantidad <= 16) return 'Octavos de Final';
    return `Ronda de ${cantidad}`;
  }
  function actualizarRanking(jugadorId: number, categoria: string, gano: boolean) {
    const jugador = db.usuarios.find((u: any) => u.id === jugadorId);
    let r = db.rankingJugadores.find((x: any) => x.jugadorId === jugadorId && x.categoria === categoria);
    if (!r) {
      r = { id: db.nextId.ranking++, jugadorId, jugadorNombre: nombreCompleto(jugador), jugadorEmail: jugador.email, categoria, puntos: 0, partidosJugados: 0, partidosGanados: 0 };
      db.rankingJugadores.push(r);
    }
    r.partidosJugados += 1;
    if (gano) { r.partidosGanados += 1; r.puntos += 3; } else { r.puntos += 1; }
  }

  if (segs[0] === 'torneos' && segs.length === 1 && req.method === 'GET') {
    let lista = db.torneos as any[];
    const estado = req.params.get('estado'); const categoria = req.params.get('categoria'); const tipo = req.params.get('tipo');
    if (estado) lista = lista.filter((t: any) => t.estado === estado);
    if (categoria) lista = lista.filter((t: any) => t.categoria === categoria);
    if (tipo) lista = lista.filter((t: any) => t.tipo === tipo);
    lista = [...lista].sort((a: any, b: any) => (a.fechaInicio < b.fechaInicio ? 1 : -1));
    return ok(lista.map(torneoConCupo));
  }
  if (segs[0] === 'torneos' && segs.length === 1 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const body = req.body as any;
    if (body.fechaFin < body.fechaInicio) return err(400, 'La fecha de fin no puede ser anterior a la fecha de inicio');
    const t = { id: db.nextId.torneo++, ...body, estado: 'INSCRIPCION_ABIERTA', createdAt: nowIso() };
    db.torneos.push(t); persist();
    return ok(torneoConCupo(t), 201);
  }
  if (segs[0] === 'torneos' && segs.length === 2 && !isNaN(+segs[1]) && req.method === 'GET') {
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    return t ? ok(torneoConCupo(t)) : err(404, 'Torneo no encontrado con ID: ' + segs[1]);
  }
  if (segs[0] === 'torneos' && segs.length === 2 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    if (t.estado !== 'INSCRIPCION_ABIERTA') return err(409, 'Sólo se puede editar un torneo mientras la inscripción está abierta');
    const body = req.body as any;
    if (body.fechaFin < body.fechaInicio) return err(400, 'La fecha de fin no puede ser anterior a la fecha de inicio');
    Object.assign(t, body); persist();
    return ok(torneoConCupo(t));
  }
  if (segs[0] === 'torneos' && segs.length === 2 && req.method === 'DELETE') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    if (t.estado === 'FINALIZADO') return err(409, 'No se puede cancelar un torneo ya finalizado');
    t.estado = 'CANCELADO'; persist();
    return ok(null, 204);
  }
  if (segs[0] === 'torneos' && segs[2] === 'inscripciones' && segs.length === 3 && req.method === 'POST') {
    if (!auth) return err(401, 'No autenticado');
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    if (t.estado !== 'INSCRIPCION_ABIERTA') return err(409, 'El torneo no admite inscripciones en su estado actual');
    const companeroEmail = (req.body as any).companeroEmail;
    if (companeroEmail.toLowerCase() === auth.email.toLowerCase()) return err(400, 'No podés inscribirte en pareja con vos mismo');
    const jugador1 = usuarioPorEmail(auth.email);
    const jugador2 = usuarioPorEmail(companeroEmail);
    if (!jugador2) return err(404, 'No se encontró un jugador con email: ' + companeroEmail);
    if (parejasInscriptas(t.id) >= t.maxParejas) return err(409, 'El torneo ya alcanzó el cupo máximo de parejas');
    const yaInscriptos = db.inscripcionesTorneo.some((i: any) => i.torneoId === t.id && i.estado === 'CONFIRMADA' &&
      ((i.jugador1Id === jugador1.id && i.jugador2Id === jugador2.id) || (i.jugador1Id === jugador2.id && i.jugador2Id === jugador1.id)));
    if (yaInscriptos) return err(400, 'Esta pareja ya está inscripta en el torneo');
    const i = {
      id: db.nextId.inscripcionTorneo++, torneoId: t.id,
      jugador1Id: jugador1.id, jugador1Nombre: nombreCompleto(jugador1), jugador1Email: jugador1.email,
      jugador2Id: jugador2.id, jugador2Nombre: nombreCompleto(jugador2), jugador2Email: jugador2.email,
      estado: 'CONFIRMADA'
    };
    db.inscripcionesTorneo.push(i); persist();
    return ok(i, 201);
  }
  if (segs[0] === 'torneos' && segs[2] === 'inscripciones' && segs.length === 3 && req.method === 'GET') {
    return ok(db.inscripcionesTorneo.filter((i: any) => i.torneoId === +segs[1]));
  }
  if (segs[0] === 'torneos' && segs[2] === 'inscripciones' && segs.length === 4 && req.method === 'DELETE') {
    if (!auth) return err(401, 'No autenticado');
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    const i = db.inscripcionesTorneo.find((x: any) => x.id === +segs[3] && x.torneoId === +segs[1]);
    if (!i) return err(404, 'Inscripción no encontrada con ID: ' + segs[3]);
    const esJugador = i.jugador1Email === auth.email || i.jugador2Email === auth.email;
    if (!esJugador && auth.rol !== 'ADMIN') return err(403, 'No tiene permisos para cancelar esta inscripción');
    if (t.estado !== 'INSCRIPCION_ABIERTA') return err(409, 'No se puede cancelar la inscripción una vez iniciado el torneo');
    i.estado = 'CANCELADA'; persist();
    return ok(null, 204);
  }
  if (segs[0] === 'torneos' && segs[2] === 'fixture' && segs.length === 3 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    if (t.estado !== 'INSCRIPCION_ABIERTA') return err(409, 'El torneo no está en estado de inscripción abierta');
    if (db.partidosTorneo.some((p: any) => p.torneoId === t.id)) return err(409, 'El fixture de este torneo ya fue generado');
    const inscripciones = db.inscripcionesTorneo.filter((i: any) => i.torneoId === t.id && i.estado === 'CONFIRMADA');
    if (inscripciones.length < 2) return err(400, 'Se necesitan al menos 2 parejas inscriptas para generar el fixture');

    const nuevos: any[] = [];
    if (t.formato === 'LIGA_TODOS_CONTRA_TODOS') {
      for (let i = 0; i < inscripciones.length; i++) {
        for (let j = i + 1; j < inscripciones.length; j++) {
          nuevos.push({
            id: db.nextId.partidoTorneo++, torneoId: t.id, ronda: 'Liga', numeroRonda: 1,
            inscripcion1Id: inscripciones[i].id, inscripcion1Nombres: `${inscripciones[i].jugador1Nombre} / ${inscripciones[i].jugador2Nombre}`,
            inscripcion2Id: inscripciones[j].id, inscripcion2Nombres: `${inscripciones[j].jugador1Nombre} / ${inscripciones[j].jugador2Nombre}`,
            fechaHora: null, setsPareja1: null, setsPareja2: null, ganadorInscripcionId: null, estado: 'PENDIENTE'
          });
        }
      }
    } else {
      const barajadas = [...inscripciones].sort(() => Math.random() - 0.5);
      const ronda = nombreRondaEliminacion(barajadas.length);
      for (let i = 0; i < barajadas.length; i += 2) {
        const p1 = barajadas[i]; const p2 = barajadas[i + 1];
        const esBye = !p2;
        nuevos.push({
          id: db.nextId.partidoTorneo++, torneoId: t.id, ronda, numeroRonda: 1,
          inscripcion1Id: p1.id, inscripcion1Nombres: `${p1.jugador1Nombre} / ${p1.jugador2Nombre}`,
          inscripcion2Id: p2 ? p2.id : null, inscripcion2Nombres: p2 ? `${p2.jugador1Nombre} / ${p2.jugador2Nombre}` : null,
          fechaHora: null, setsPareja1: null, setsPareja2: null,
          ganadorInscripcionId: esBye ? p1.id : null, estado: esBye ? 'FINALIZADO' : 'PENDIENTE'
        });
      }
    }
    db.partidosTorneo.push(...nuevos);
    t.estado = 'EN_CURSO'; persist();
    return ok(nuevos, 201);
  }
  if (segs[0] === 'torneos' && segs[2] === 'partidos' && segs.length === 3 && req.method === 'GET') {
    return ok(db.partidosTorneo.filter((p: any) => p.torneoId === +segs[1]).sort((a: any, b: any) => a.numeroRonda - b.numeroRonda || a.id - b.id));
  }
  if (segs[0] === 'torneos' && segs[2] === 'partidos' && segs[4] === 'resultado' && segs.length === 5 && req.method === 'PUT') {
    const denied = requireRol(auth, ['ADMIN', 'RECEPCIONISTA']); if (denied) return denied;
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    const p = db.partidosTorneo.find((x: any) => x.id === +segs[3] && x.torneoId === +segs[1]);
    if (!p) return err(404, 'Partido no encontrado con ID: ' + segs[3]);
    if (p.estado === 'FINALIZADO') return err(409, 'El resultado de este partido ya fue cargado');
    if (!p.inscripcion2Id) return err(409, 'Este partido es un bye y no admite carga de resultado');
    const body = req.body as any;
    if (body.setsPareja1 === body.setsPareja2) return err(400, 'El resultado no puede terminar en empate');

    const ganadorEsP1 = body.setsPareja1 > body.setsPareja2;
    const iGanadora = db.inscripcionesTorneo.find((x: any) => x.id === (ganadorEsP1 ? p.inscripcion1Id : p.inscripcion2Id));
    const iPerdedora = db.inscripcionesTorneo.find((x: any) => x.id === (ganadorEsP1 ? p.inscripcion2Id : p.inscripcion1Id));

    p.setsPareja1 = body.setsPareja1; p.setsPareja2 = body.setsPareja2;
    p.ganadorInscripcionId = iGanadora.id; p.estado = 'FINALIZADO';

    actualizarRanking(iGanadora.jugador1Id, t.categoria, true);
    actualizarRanking(iGanadora.jugador2Id, t.categoria, true);
    actualizarRanking(iPerdedora.jugador1Id, t.categoria, false);
    actualizarRanking(iPerdedora.jugador2Id, t.categoria, false);
    persist();
    return ok(p);
  }
  if (segs[0] === 'torneos' && segs[2] === 'siguiente-ronda' && segs.length === 3 && req.method === 'POST') {
    const denied = requireRol(auth, ['ADMIN']); if (denied) return denied;
    const t = db.torneos.find((x: any) => x.id === +segs[1]);
    if (!t) return err(404, 'Torneo no encontrado con ID: ' + segs[1]);
    if (t.formato !== 'ELIMINACION_DIRECTA') return err(400, 'Sólo los torneos de eliminación directa avanzan por rondas');
    const partidosDelTorneo = db.partidosTorneo.filter((p: any) => p.torneoId === t.id);
    if (partidosDelTorneo.length === 0) return err(409, 'Primero hay que generar el fixture del torneo');
    const ultimaRonda = Math.max(...partidosDelTorneo.map((p: any) => p.numeroRonda));
    const partidosRondaActual = partidosDelTorneo.filter((p: any) => p.numeroRonda === ultimaRonda);
    if (!partidosRondaActual.every((p: any) => p.estado === 'FINALIZADO')) return err(409, 'Aún hay partidos pendientes en la ronda actual');

    if (partidosRondaActual.length === 1) {
      t.estado = 'FINALIZADO'; persist();
      return ok([], 201);
    }

    const ganadoresIds = partidosRondaActual.map((p: any) => p.ganadorInscripcionId);
    const ganadores = ganadoresIds.map((id: number) => db.inscripcionesTorneo.find((i: any) => i.id === id));
    const numeroRonda = ultimaRonda + 1;
    const ronda = nombreRondaEliminacion(ganadores.length);
    const nuevos: any[] = [];
    for (let i = 0; i < ganadores.length; i += 2) {
      const p1 = ganadores[i]; const p2 = ganadores[i + 1];
      const esBye = !p2;
      nuevos.push({
        id: db.nextId.partidoTorneo++, torneoId: t.id, ronda, numeroRonda,
        inscripcion1Id: p1.id, inscripcion1Nombres: `${p1.jugador1Nombre} / ${p1.jugador2Nombre}`,
        inscripcion2Id: p2 ? p2.id : null, inscripcion2Nombres: p2 ? `${p2.jugador1Nombre} / ${p2.jugador2Nombre}` : null,
        fechaHora: null, setsPareja1: null, setsPareja2: null,
        ganadorInscripcionId: esBye ? p1.id : null, estado: esBye ? 'FINALIZADO' : 'PENDIENTE'
      });
    }
    db.partidosTorneo.push(...nuevos); persist();
    return ok(nuevos, 201);
  }

  // ---- RANKINGS ----
  if (segs[0] === 'rankings' && segs.length === 3 && segs[2] === 'mi-posicion' && req.method === 'GET') {
    if (!auth) return err(401, 'No autenticado');
    const r = db.rankingJugadores.find((x: any) => x.categoria === segs[1] && x.jugadorEmail === auth.email);
    return r ? ok(r) : err(404, 'Todavía no tenés ranking en la categoría ' + segs[1]);
  }
  if (segs[0] === 'rankings' && segs.length === 2 && req.method === 'GET') {
    const lista = db.rankingJugadores.filter((x: any) => x.categoria === segs[1])
      .sort((a: any, b: any) => b.puntos - a.puntos || b.partidosGanados - a.partidosGanados);
    return ok(lista);
  }

  // ---- SOLICITUDES DE PARTIDO ----
  if (segs[0] === 'solicitudes-partido' && segs.length === 2 && segs[1] === 'mis-solicitudes' && req.method === 'GET') {
    if (!auth) return err(401, 'No autenticado');
    return ok(db.solicitudesPartido.filter((s: any) => s.creadorEmail === auth.email).sort((a: any, b: any) => b.id - a.id));
  }
  if (segs[0] === 'solicitudes-partido' && segs.length === 2 && segs[1] === 'mis-postulaciones' && req.method === 'GET') {
    if (!auth) return err(401, 'No autenticado');
    return ok(db.solicitudesPartido.filter((s: any) => s.postulaciones.some((p: any) => p.jugadorEmail === auth.email)).sort((a: any, b: any) => b.id - a.id));
  }
  if (segs[0] === 'solicitudes-partido' && segs.length === 1 && req.method === 'POST') {
    if (!auth) return err(401, 'No autenticado');
    const body = req.body as any;
    const creador = usuarioPorEmail(auth.email);
    let cancha: any = null;
    if (body.canchaId) {
      cancha = db.canchas.find((c: any) => c.id === +body.canchaId && c.activa);
      if (!cancha) return err(404, 'Cancha no encontrada o inactiva');
      const propuesta = new Date(body.fechaHoraPropuesta);
      const dow = DIAS[propuesta.getDay()];
      const hora = `${String(propuesta.getHours()).padStart(2, '0')}:${String(propuesta.getMinutes()).padStart(2, '0')}`;
      const existeFranja = db.franjas.some((f: any) =>
        f.canchaId === cancha.id && f.diasAplicables.includes(dow) && hora >= f.horaInicio && hora < f.horaFin);
      if (!existeFranja) return err(400, 'La cancha seleccionada no tiene un horario habilitado para el día y la hora propuestos');
    }
    const s = {
      id: db.nextId.solicitud++, creadorId: creador.id, creadorNombre: nombreCompleto(creador), creadorEmail: creador.email,
      tipoSolicitud: body.tipoSolicitud, categoria: body.categoria, cantidadJugadoresFaltantes: body.cantidadJugadoresFaltantes,
      fechaHoraPropuesta: body.fechaHoraPropuesta, canchaId: cancha?.id ?? null, canchaNombre: cancha?.nombre ?? null,
      descripcion: body.descripcion ?? null, estado: 'ABIERTA', createdAt: nowIso(), postulaciones: [] as any[]
    };
    db.solicitudesPartido.push(s); persist();
    return ok(s, 201);
  }
  if (segs[0] === 'solicitudes-partido' && segs.length === 1 && req.method === 'GET') {
    const tipoSolicitud = req.params.get('tipoSolicitud'); const categoria = req.params.get('categoria'); const estadoParam = req.params.get('estado');
    let lista = db.solicitudesPartido.filter((s: any) => s.estado === (estadoParam || 'ABIERTA'));
    if (tipoSolicitud) lista = lista.filter((s: any) => s.tipoSolicitud === tipoSolicitud);
    if (categoria) lista = lista.filter((s: any) => s.categoria === categoria);
    lista = [...lista].sort((a: any, b: any) => (a.fechaHoraPropuesta < b.fechaHoraPropuesta ? -1 : 1));
    return ok(lista);
  }
  if (segs[0] === 'solicitudes-partido' && segs.length === 2 && !isNaN(+segs[1]) && req.method === 'GET') {
    const s = db.solicitudesPartido.find((x: any) => x.id === +segs[1]);
    return s ? ok(s) : err(404, 'Solicitud no encontrada con ID: ' + segs[1]);
  }
  if (segs[0] === 'solicitudes-partido' && segs.length === 2 && req.method === 'DELETE') {
    if (!auth) return err(401, 'No autenticado');
    const s = db.solicitudesPartido.find((x: any) => x.id === +segs[1]);
    if (!s) return err(404, 'Solicitud no encontrada con ID: ' + segs[1]);
    if (s.creadorEmail !== auth.email) return err(403, 'No tiene permisos para operar sobre esta solicitud');
    if (s.estado === 'CANCELADA') return err(409, 'La solicitud ya está cancelada');
    s.estado = 'CANCELADA'; persist();
    return ok(null, 204);
  }
  if (segs[0] === 'solicitudes-partido' && segs[2] === 'postulaciones' && segs.length === 3 && req.method === 'POST') {
    if (!auth) return err(401, 'No autenticado');
    const s = db.solicitudesPartido.find((x: any) => x.id === +segs[1]);
    if (!s) return err(404, 'Solicitud no encontrada con ID: ' + segs[1]);
    if (s.estado !== 'ABIERTA') return err(409, 'La solicitud ya no admite postulaciones');
    if (s.creadorEmail === auth.email) return err(400, 'No podés postularte a tu propia solicitud');
    const jugador = usuarioPorEmail(auth.email);
    if (s.postulaciones.some((p: any) => p.jugadorEmail === auth.email)) return err(400, 'Ya te postulaste a esta solicitud');
    s.postulaciones.push({
      id: db.nextId.postulacion++, solicitudId: s.id, jugadorId: jugador.id, jugadorNombre: nombreCompleto(jugador),
      jugadorEmail: jugador.email, mensaje: (req.body as any).mensaje ?? null, estado: 'PENDIENTE', createdAt: nowIso()
    });
    persist();
    return ok(s, 201);
  }
  if (segs[0] === 'solicitudes-partido' && segs[2] === 'postulaciones' && (segs[4] === 'aceptar' || segs[4] === 'rechazar') && segs.length === 5 && req.method === 'PUT') {
    if (!auth) return err(401, 'No autenticado');
    const s = db.solicitudesPartido.find((x: any) => x.id === +segs[1]);
    if (!s) return err(404, 'Solicitud no encontrada con ID: ' + segs[1]);
    if (s.creadorEmail !== auth.email) return err(403, 'No tiene permisos para operar sobre esta solicitud');
    const p = s.postulaciones.find((x: any) => x.id === +segs[3]);
    if (!p) return err(404, 'Postulación no encontrada con ID: ' + segs[3]);
    if (p.estado !== 'PENDIENTE') return err(409, 'Esta postulación ya fue resuelta');

    if (segs[4] === 'rechazar') {
      p.estado = 'RECHAZADA'; persist();
      return ok(s);
    }

    if (s.estado !== 'ABIERTA') return err(409, 'La solicitud ya no está abierta');
    p.estado = 'ACEPTADA';
    const aceptadas = s.postulaciones.filter((x: any) => x.estado === 'ACEPTADA').length;
    if (aceptadas >= s.cantidadJugadoresFaltantes) {
      s.estado = 'COMPLETA';
      s.postulaciones.filter((x: any) => x.estado === 'PENDIENTE').forEach((x: any) => x.estado = 'RECHAZADA');
    }
    persist();
    return ok(s);
  }

  return err(404, `Mock no implementado para ${req.method} ${path}`);
};
