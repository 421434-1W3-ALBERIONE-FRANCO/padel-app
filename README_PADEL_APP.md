# PadelApp — Sistema de Gestión de Canchas de Pádel

> **Documento maestro v1.0** | Prompt de contexto para agente de desarrollo IA  
> Stack: Java 21 · Spring Boot 3 · Angular 18 · Tailwind CSS v4 · PostgreSQL · Redis · MercadoPago · WhatsApp Business API

---

## Prompt de contexto para el agente de IA

```
Sos el agente de desarrollo del proyecto PadelApp. Tu rol es generar código de producción
profesional, limpio y escalable siguiendo estrictamente las skills de arquitectura definidas
en este documento. Antes de generar cualquier artefacto de código, debés leer y aplicar
las restricciones de la skill correspondiente (backend o frontend). Nunca saltees capas
arquitectónicas, nunca expongas entidades JPA en endpoints públicos, y nunca mezcles
lógica de negocio en controladores o vistas. Cuando el usuario te pida una tarea, identificá
a qué capa pertenece y aplicá el estándar correspondiente sin excepciones.
```

---

## Skills de arquitectura mandatorias

### Backend — `utn_backend_springboot_master` v2.0.0

**Stack:** Java 21 · Spring Boot 3.x · Lombok · Spring Data JPA · Spring Security · Spring Data Redis

**Reglas de arquitectura:**

- Patrón multicapa estricto: `Controller → Service → Repository`. Sin salteos de capa.
- Todas las dependencias declaradas como `private final` con `@RequiredArgsConstructor`. **PROHIBIDO `@Autowired` sobre campos.**
- Controllers decorados con `@RestController` + `@RequestMapping("/api/v1/recursos")` en plural.
- Verbos HTTP correctos: `GET` (200), `POST` (201 CREATED), `PUT` (200), `DELETE` (204 NO CONTENT). Todo envuelto en `ResponseEntity<T>`.
- **PROHIBIDO** exponer entidades `@Entity` en endpoints. Usar DTOs `record` para entrada (`RequestDTO`) y salida (`ResponseDTO`).
- Control de errores centralizado con `@RestControllerAdvice` → `GlobalExceptionHandler`. Respuesta unificada tipo `ApiError` con: `timestamp`, `status`, `error`, `message`, `path`.
- **PROHIBIDO** bloques `try-catch` en controllers o services para excepciones de negocio.
- **PROHIBIDO** URLs orientadas a acciones (`/api/crearUsuario`). Solo recursos + verbos HTTP.

**Checklist de validación antes de entregar código:**
- [ ] Dependencias `private final` vía constructor Lombok
- [ ] Controllers libres de lógica y try-catch
- [ ] Cliente recibe siempre DTOs, nunca entidades
- [ ] Excepciones manejadas centralizadamente con JSON uniforme

---

### Frontend — `utn_frontend_angular_master` v2.0.0

**Stack:** Angular 18 (Signals + Standalone Components) · Tailwind CSS v4 · TypeScript estricto · RxJS

**Reglas de arquitectura:**

- Arquitectura limpia por capas: `Component → Service → HTTP Client`. Sin lógica de negocio en componentes.
- State management con Signals de Angular (`signal()`, `computed()`, `effect()`). Estado inmutable, cada mutación retorna estado nuevo.
- Componentes standalone, sin módulos clásicos (`NgModule`). Imports directos en cada componente.
- **PROHIBIDO** llamadas HTTP directas desde componentes. Todo tráfico de red pasa por un `Service` inyectado.
- DTOs tipados en TypeScript para cada request y response. Sin uso de `any`.
- Manejo explícito de tres estados en pantalla: **Cargando** (skeleton/spinner), **Éxito** (render de datos), **Error** (banner de feedback).
- Diseño Mobile-First con Tailwind, responsive con breakpoints `sm:` `md:` `lg:`.
- `@RestControllerAdvice` en el back mapea a interceptor HTTP en el front (`HttpInterceptor`) para manejo global de errores.

**Checklist de validación antes de entregar código:**
- [ ] HTML semántico y 100% responsive
- [ ] Lógica de negocio en services, no en componentes
- [ ] Atributos ARIA para accesibilidad
- [ ] Tres estados visuales implementados (loading / success / error)

---

## Arquitectura general del sistema

```
┌─────────────────────────────────────────────────────┐
│                   CLIENTE                           │
│         Angular 18 PWA · Tailwind CSS v4            │
└───────────────────┬─────────────────────────────────┘
                    │ HTTPS / REST + WebSocket
┌───────────────────▼─────────────────────────────────┐
│              SPRING BOOT 3 API                      │
│  Controller → Service → Repository                  │
│  Spring Security · JWT · Spring Data JPA            │
└──────┬──────────────────────┬───────────────────────┘
       │                      │
┌──────▼──────┐     ┌─────────▼──────┐
│ PostgreSQL  │     │     Redis      │
│  (datos     │     │  (bloqueo de   │
│  persistente│     │   slots, caché)│
└─────────────┘     └────────────────┘
       │
┌──────▼──────────────────────────────────────────────┐
│              INTEGRACIONES EXTERNAS                 │
│  MercadoPago SDK · WhatsApp Business API (Twilio)   │
│  Resend (email)                                     │
└─────────────────────────────────────────────────────┘
```

---

## Modelo de datos — Entidades core

### Diagrama de relaciones

```
USUARIO ──────────< RESERVA >────────── CANCHA
                       │                   │
                       │              FRANJA_HORARIA
                       │
              ┌────────┼────────┬──────────┐
              │        │        │          │
            PAGO    CONSUMO  USO_BONO  NOTIFICACION
                       │        │
                   PRODUCTO    BONO
```

### Entidades y campos clave

| Entidad | Campos principales | Notas |
|---|---|---|
| `USUARIO` | id, nombre, apellido, email, telefono, password_hash, rol, activo | Roles: `ADMIN`, `RECEPCIONISTA`, `JUGADOR` |
| `CANCHA` | id, nombre, tipo_suelo, techada, tiene_luz, activa | tipo_suelo: `BLINDEX`, `CEMENTO`, `SINTETICO` |
| `FRANJA_HORARIA` | id, cancha_id, hora_inicio, hora_fin, duracion_min, precio_base, precio_nocturno, dias_aplicables | Independiente de reservas (snapshot de precio al reservar) |
| `RESERVA` | id, usuario_id, cancha_id, franja_id, fecha, hora_inicio, hora_fin, precio_total, senya_pagada, estado, origen | Estados: `PENDIENTE_PAGO`, `CONFIRMADA`, `CANCELADA`, `COMPLETADA`, `NO_SHOW` |
| `BLOQUEO_CANCHA` | id, cancha_id, fecha_desde, fecha_hasta, hora_desde, hora_hasta, motivo | Para mantenimiento, lluvia, torneos |
| `PAGO` | id, reserva_id, usuario_id, monto, metodo, estado, mp_preference_id, mp_payment_id | Integración MercadoPago |
| `BONO` | id, usuario_id, tipo, horas_totales, horas_usadas, precio_pagado, fecha_vencimiento, estado | Prepago de horas |
| `USO_BONO` | id, bono_id, reserva_id, horas_descontadas | Pivot auditable de descuentos |
| `PRODUCTO` | id, nombre, categoria, precio, stock, activo | Tienda / cantina |
| `CONSUMO` | id, reserva_id, usuario_id, producto_id, cantidad, precio_unitario, subtotal, estado_pago | Tab por jugador |
| `NOTIFICACION` | id, usuario_id, reserva_id, canal, tipo, estado, mensaje, enviada_at, intentos | Auditoría de WhatsApp/email |

---

## Estructura de paquetes — Backend

```
com.padel
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   └── WebSocketConfig.java
├── controller/
│   ├── UsuarioController.java
│   ├── CanchaController.java
│   ├── ReservaController.java
│   ├── PagoController.java
│   ├── BonoController.java
│   └── ConsumoController.java
├── service/
│   ├── UsuarioService.java
│   ├── ReservaService.java       ← lógica de bloqueo optimista con Redis
│   ├── PagoService.java          ← integración MercadoPago
│   ├── NotificacionService.java  ← WhatsApp + email
│   └── impl/
├── repository/
│   ├── UsuarioRepository.java
│   ├── CanchaRepository.java
│   ├── ReservaRepository.java
│   └── ...
├── domain/
│   ├── entity/
│   │   ├── Usuario.java
│   │   ├── Cancha.java
│   │   ├── FranjaHoraria.java
│   │   ├── Reserva.java
│   │   └── ...
│   └── enums/
│       ├── EstadoReserva.java
│       ├── RolUsuario.java
│       ├── TipoSuelo.java
│       └── CanalNotificacion.java
├── dto/
│   ├── request/
│   │   ├── CrearReservaRequest.java
│   │   ├── CrearUsuarioRequest.java
│   │   └── ...
│   └── response/
│       ├── ReservaResponse.java
│       ├── DisponibilidadResponse.java
│       └── ...
├── mapper/
│   ├── ReservaMapper.java        ← MapStruct
│   └── UsuarioMapper.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   ├── SlotNoDisponibleException.java
│   └── ApiError.java
└── util/
    ├── FechaHoraUtil.java
    └── PrecioCalculator.java
```

---

## Estructura de módulos — Frontend Angular

```
src/app/
├── core/
│   ├── interceptors/
│   │   └── auth.interceptor.ts       ← JWT + manejo global errores
│   ├── guards/
│   │   └── auth.guard.ts
│   └── services/
│       ├── auth.service.ts
│       └── reserva.service.ts
├── shared/
│   ├── components/
│   │   ├── loading-spinner/
│   │   └── error-banner/
│   └── models/
│       ├── reserva.model.ts          ← DTOs tipados
│       └── usuario.model.ts
├── features/
│   ├── reservas/
│   │   ├── calendario/               ← Vista principal de disponibilidad
│   │   └── checkout/                 ← Flujo de pago
│   ├── bonos/
│   ├── tienda/
│   └── admin/
│       ├── dashboard/
│       ├── canchas/
│       └── reportes/
└── app.config.ts                     ← Providers standalone
```

---

## Fases de desarrollo

### Fase 1 — Base y autenticación *(Sprint 1 y 2)*

**Objetivo:** Proyecto levantando, BD conectada, primer endpoint funcionando.

**Tareas backend:**
- [ ] Crear proyecto Spring Boot con Spring Initializr (Web, JPA, H2, Lombok, Validation, DevTools)
- [ ] Configurar `application.yml` con H2 en memoria para desarrollo local
- [ ] Crear entidad `Usuario` con JPA + enum `RolUsuario`
- [ ] Crear `UsuarioRepository`, `UsuarioService`, `UsuarioController` siguiendo skill backend
- [ ] Implementar `GlobalExceptionHandler` con `ApiError`
- [ ] Implementar JWT: `JwtUtil`, `JwtFilter`, `SecurityConfig`
- [ ] Endpoints: `POST /api/v1/auth/registro`, `POST /api/v1/auth/login`
- [ ] Test con Bruno/Postman: registro → login → token

**Tareas frontend:**
- [ ] Crear proyecto Angular 18 standalone con Tailwind CSS v4
- [ ] Configurar `HttpClient` con interceptor de auth
- [ ] Pantallas: Login, Registro
- [ ] `AuthService` con signal de usuario autenticado
- [ ] Guards de ruta por rol

**Entregable:** Usuario puede registrarse, loguearse y recibir JWT. Frontend muestra dashboard básico según rol.

---

### Fase 2 — Canchas y disponibilidad *(Sprint 3)*

**Objetivo:** Admin puede cargar canchas y franjas. Usuario ve el calendario en tiempo real.

**Tareas backend:**
- [ ] Entidades: `Cancha`, `FranjaHoraria`, `BloqueoCancha`
- [ ] CRUD completo de canchas (`/api/v1/canchas`)
- [ ] CRUD de franjas horarias (`/api/v1/canchas/{id}/franjas`)
- [ ] Endpoint de disponibilidad: `GET /api/v1/disponibilidad?fecha=&cancha=`
- [ ] Lógica de disponibilidad: excluir reservas confirmadas + bloqueos activos
- [ ] WebSocket config para notificar cambios en tiempo real

**Tareas frontend:**
- [ ] Grilla de disponibilidad (calendario semanal por cancha)
- [ ] Componente de selección de fecha/horario con estado de slot (libre/ocupado/bloqueado)
- [ ] Panel admin: ABM de canchas con formulario
- [ ] Loading skeleton mientras carga disponibilidad

**Entregable:** Usuario ve en tiempo real qué canchas y horarios están disponibles.

---

### Fase 3 — Reservas y WhatsApp *(Sprint 4)*

**Objetivo:** Flujo completo de reserva con confirmación automática por WhatsApp.

**Tareas backend:**
- [ ] Entidad `Reserva` + enum `EstadoReserva`
- [ ] `ReservaService` con bloqueo optimista (Redis `SETNX` con TTL 10 min)
- [ ] Endpoints: `POST /api/v1/reservas`, `PUT /api/v1/reservas/{id}/cancelar`, `GET /api/v1/reservas/mis-reservas`
- [ ] Lógica de negocio: anticipación mínima, política de cancelación
- [ ] Integración Twilio WhatsApp: `NotificacionService`
- [ ] Templates WhatsApp: confirmación de reserva, recordatorio 2h antes
- [ ] Scheduler (`@Scheduled`) para recordatorios automáticos
- [ ] Entidad `Notificacion` con auditoría de envíos y retry

**Tareas frontend:**
- [ ] Flujo de reserva paso a paso (cancha → fecha → horario → confirmación)
- [ ] Pantalla "Mis reservas" con historial y opción de cancelar
- [ ] Feedback visual de confirmación post-reserva

**Entregable:** Usuario reserva una cancha, recibe WhatsApp de confirmación y recordatorio automático 2 horas antes.

---

### Fase 4 — Pagos online y bonos *(Sprint 5)*

**Objetivo:** Cobro de seña online con MercadoPago. Venta y uso de bonos prepago.

**Tareas backend:**
- [ ] Entidades: `Pago`, `Bono`, `UsoBono`
- [ ] Integración MercadoPago SDK: crear preference, procesar webhook
- [ ] `PagoService`: generar link de pago, confirmar pago por webhook
- [ ] Endpoint webhook: `POST /api/v1/pagos/webhook/mercadopago`
- [ ] Lógica de bonos: compra, descuento automático en reserva, control de vencimiento
- [ ] Notificación WhatsApp al confirmar pago y al bono por vencer

**Tareas frontend:**
- [ ] Checkout integrado con MercadoPago Checkout Pro (redirect)
- [ ] Pantalla de compra de bonos con resumen de saldo disponible
- [ ] Indicador de saldo de bono en header para usuario autenticado

**Entregable:** Usuario puede pagar la seña online y usar su bono de horas al reservar.

---

### Fase 5 — Tienda y consumos *(Sprint 6)*

**Objetivo:** Recepcionista carga consumos al turno. Usuario ve su tab y puede pagar desde la app.

**Tareas backend:**
- [ ] Entidades: `Producto`, `Consumo`
- [ ] CRUD de productos (`/api/v1/productos`) — solo admin/recepcionista
- [ ] Endpoint cargar consumo a reserva: `POST /api/v1/reservas/{id}/consumos`
- [ ] Endpoint ver tab: `GET /api/v1/reservas/{id}/consumos`
- [ ] Endpoint cerrar cuenta: `PUT /api/v1/reservas/{id}/cerrar-cuenta`
- [ ] Impacto en caja diaria (suma de pagos del día por método)

**Tareas frontend:**
- [ ] Panel recepcionista: vista de reservas del día + carga de consumos
- [ ] Vista POS simplificada: selección de producto → agregar al tab
- [ ] Pantalla de cierre de cuenta con detalle de consumos y total

**Entregable:** Flujo completo de cantina vinculado al turno, con cierre y cobro integrado.

---

### Fase 6 — Dashboard admin y deploy *(Sprint 7)*

**Objetivo:** Panel de métricas, reportes y sistema listo para producción.

**Tareas backend:**
- [ ] Endpoints de métricas: ocupación por cancha, ingresos por período, productos más vendidos
- [ ] Reporte diario de caja exportable
- [ ] Migrar de H2 a PostgreSQL: crear scripts Flyway `V1__...sql`
- [ ] Integrar Redis para bloqueo de slots en producción
- [ ] Agregar Spring Security con roles completos
- [ ] Configurar variables de entorno con `application-prod.yml`

**Tareas frontend:**
- [ ] Dashboard admin con gráficos de ocupación e ingresos (Chart.js o ApexCharts)
- [ ] Tabla de reservas con filtros por fecha, cancha, estado
- [ ] Configuración PWA: manifest + service worker
- [ ] Manejo de modo offline básico

**Infraestructura:**
- [ ] `Dockerfile` para Spring Boot
- [ ] `Dockerfile` para Angular (build + Nginx)
- [ ] `docker-compose.yml` con Postgres + Redis + backend + frontend
- [ ] CI/CD con GitHub Actions: build → test → deploy
- [ ] Deploy en Railway o Render

**Entregable:** Sistema completo en producción, accesible desde mobile como PWA.

---

## Flujo de reserva — Descripción técnica para el agente

```
1. Usuario selecciona cancha + fecha + franja horaria
2. Frontend llama GET /api/v1/disponibilidad → muestra slots libres
3. Usuario confirma slot → Frontend llama POST /api/v1/reservas
4. ReservaService intenta SETNX en Redis con key "slot:{cancha}:{fecha}:{hora}" TTL=600s
   - Si falla: SlotNoDisponibleException → 409 CONFLICT
   - Si OK: crea Reserva con estado PENDIENTE_PAGO en Postgres
5. PagoService genera MercadoPago preference → devuelve link de pago
6. Usuario paga en MercadoPago
7. Webhook MP llega a POST /api/v1/pagos/webhook/mercadopago
8. PagoService actualiza Pago → ReservaService actualiza estado a CONFIRMADA
9. Redis libera el lock (ya no es necesario, la reserva está en Postgres)
10. NotificacionService dispara WhatsApp de confirmación
11. Scheduler: 2h antes del turno dispara WhatsApp de recordatorio
```

---

## Variables de entorno requeridas

```env
# Base de datos
DB_URL=jdbc:postgresql://localhost:5432/padel_db
DB_USERNAME=padel_user
DB_PASSWORD=padel123

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=tu_clave_secreta_muy_larga
JWT_EXPIRATION=86400000

# MercadoPago
MP_ACCESS_TOKEN=tu_access_token
MP_WEBHOOK_SECRET=tu_webhook_secret

# Twilio WhatsApp
TWILIO_ACCOUNT_SID=tu_sid
TWILIO_AUTH_TOKEN=tu_token
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886

# Email (Resend)
RESEND_API_KEY=tu_api_key
RESEND_FROM=noreply@tudominio.com
```

---

## Convenciones de código

| Elemento | Convención | Ejemplo |
|---|---|---|
| Entidades JPA | PascalCase singular | `Reserva`, `FranjaHoraria` |
| Tablas BD | snake_case plural | `reservas`, `franjas_horarias` |
| Endpoints | kebab-case plural | `/api/v1/franjas-horarias` |
| DTOs | PascalCase + sufijo | `CrearReservaRequest`, `ReservaResponse` |
| Enums | SCREAMING_SNAKE_CASE | `PENDIENTE_PAGO`, `NO_SHOW` |
| Servicios | PascalCase + Service | `ReservaService` |
| Tests | nombre + should + resultado | `deberiaFallarSiSlotOcupado()` |

---

## Estado actual del proyecto

| Fase | Estado | Notas |
|---|---|---|
| Modelo de datos | ✅ Diseñado | ERD completo, 11 entidades |
| Setup proyecto | ✅ Completo | Spring Boot 3 + Angular 18 inicializados |
| Fase 1 — Auth | ✅ Completo | JWT, H2, login/registro frontend y backend |
| Fase 2 — Canchas y Disponibilidad | ✅ Completo | CRUDs, Bloqueos, Disponibilidad, WS y Frontend |
| Fase 3 — Reservas y WhatsApp | ✅ Completo | Locks Redis, Twilio WhatsApp, Scheduler, Modal, Historial y Cancelación |
| Fase 4 — Pagos y Bonos | ⏳ Pendiente | Integración de seña con MercadoPago |
| Fase 5 — Tienda y Consumos | ⏳ Pendiente | Consumos a turnos y reportes POS |
| Fase 6 — Deploy y Dashboard | ⏳ Pendiente | Postgres físico, Docker y despliegue |

---

*Última actualización: 23/06/2026*
