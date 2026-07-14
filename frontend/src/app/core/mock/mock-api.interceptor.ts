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
    nextId: { cancha: 3, franja: 3, bloqueo: 1, reserva: 1, bono: 2, producto: 4, usuario: 4 },
    usuarios: [
      { id: 1, nombre: 'Admin', apellido: 'Dev', email: 'admin@test.com', telefono: '', rol: 'ADMIN', activo: true, password: 'admin123' },
      { id: 2, nombre: 'Juan', apellido: 'Jugador', email: 'juan@test.com', telefono: '', rol: 'JUGADOR', activo: true, password: 'test123' }
    ],
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
    if (raw) return JSON.parse(raw);
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

  return err(404, `Mock no implementado para ${req.method} ${path}`);
};
