import { CategoriaJugador, EstadoTorneo, FormatoTorneo, TipoTorneo } from '../../../shared/models/torneo.model';

/** Color de cada categoría, de la primera (oro) a la octava (verde): los pósters de la lista y
 * el encabezado del detalle se reconocen de un vistazo. */
const COLOR_CATEGORIA: Record<CategoriaJugador, string> = {
  PRIMERA: '#fbbf24',
  SEGUNDA: '#fb923c',
  TERCERA: '#f87171',
  CUARTA: '#f472b6',
  QUINTA: '#c084fc',
  SEXTA: '#60a5fa',
  SEPTIMA: '#22d3ee',
  OCTAVA: '#72db28'
};

const DATOS_CATEGORIA: Record<CategoriaJugador, { numero: number; nombre: string }> = {
  PRIMERA: { numero: 1, nombre: 'Primera' },
  SEGUNDA: { numero: 2, nombre: 'Segunda' },
  TERCERA: { numero: 3, nombre: 'Tercera' },
  CUARTA: { numero: 4, nombre: 'Cuarta' },
  QUINTA: { numero: 5, nombre: 'Quinta' },
  SEXTA: { numero: 6, nombre: 'Sexta' },
  SEPTIMA: { numero: 7, nombre: 'Séptima' },
  OCTAVA: { numero: 8, nombre: 'Octava' }
};

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function colorCategoria(categoria: CategoriaJugador): string {
  return COLOR_CATEGORIA[categoria] ?? '#82828f';
}

/** "4ª" para CUARTA: el número grande del póster. */
export function numeroCategoria(categoria: CategoriaJugador): string {
  const datos = DATOS_CATEGORIA[categoria];
  return datos ? `${datos.numero}ª` : '';
}

export function nombreCategoria(categoria: CategoriaJugador): string {
  return DATOS_CATEGORIA[categoria]?.nombre ?? categoria;
}

/** Fondo del póster: un resplandor del color de la categoría sobre el gris oscuro de la app. */
export function fondoPoster(categoria: CategoriaJugador): string {
  const color = colorCategoria(categoria);
  return `radial-gradient(120% 95% at 100% 0%, ${color}59 0%, transparent 58%), ` +
    `linear-gradient(165deg, ${color}2e 0%, rgba(10, 10, 14, 0) 62%)`;
}

export function etiquetaFormato(formato: FormatoTorneo): string {
  return formato === 'LIGA_TODOS_CONTRA_TODOS' ? 'Todos contra todos' : 'Eliminación directa';
}

export function etiquetaTipo(tipo: TipoTorneo): string {
  switch (tipo) {
    case 'LIGA': return 'Liga';
    case 'TORNEO_EXPRESS': return 'Express';
    default: return 'Torneo';
  }
}

export function etiquetaEstado(estado: EstadoTorneo): string {
  switch (estado) {
    case 'INSCRIPCION_ABIERTA': return 'Inscripción abierta';
    case 'EN_CURSO': return 'En juego';
    case 'SUSPENDIDO': return 'Suspendido';
    case 'FINALIZADO': return 'Finalizado';
    case 'CANCELADO': return 'Cancelado';
    default: return estado;
  }
}

export function claseEstadoTorneo(estado: EstadoTorneo): string {
  switch (estado) {
    case 'INSCRIPCION_ABIERTA': return 'bg-primary-500/15 text-primary-300 border-primary-500/30';
    case 'EN_CURSO': return 'bg-turquoise-500/15 text-turquoise-300 border-turquoise-500/30';
    case 'SUSPENDIDO': return 'bg-amber-500/15 text-amber-300 border-amber-500/30';
    case 'CANCELADO': return 'bg-red-500/15 text-red-300 border-red-500/30';
    default: return 'bg-white/5 text-slate-300 border-white/10';
  }
}

/** Fecha local a partir de "yyyy-MM-dd": `new Date('2026-10-26')` la tomaría en UTC y en
 * Argentina mostraría el día anterior. */
export function fechaLocal(fechaIso: string): Date {
  const [anio, mes, dia] = fechaIso.substring(0, 10).split('-').map(Number);
  return new Date(anio, mes - 1, dia);
}

/** "26 oct" */
export function fechaCorta(fechaIso: string): string {
  const fecha = fechaLocal(fechaIso);
  return `${fecha.getDate()} ${MESES[fecha.getMonth()]}`;
}

/** Días de calendario entre hoy y la fecha (negativo si ya pasó). */
export function diasHasta(fechaIso: string, ahora: Date = new Date()): number {
  const hoy = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
  return Math.round((fechaLocal(fechaIso).getTime() - hoy.getTime()) / 86_400_000);
}

export interface CuentaRegresiva {
  dias: number;
  horas: number;
  minutos: number;
}

/** Lo que falta hasta las 00:00 (hora local) del día indicado; null si ese momento ya llegó. */
export function cuentaRegresiva(fechaIso: string, ahora: Date = new Date()): CuentaRegresiva | null {
  let minutos = Math.floor((fechaLocal(fechaIso).getTime() - ahora.getTime()) / 60_000);
  if (minutos <= 0) return null;
  const dias = Math.floor(minutos / 1440);
  minutos -= dias * 1440;
  const horas = Math.floor(minutos / 60);
  return { dias, horas, minutos: minutos - horas * 60 };
}

export function dosDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

/** "Juan Pérez / Lucía Fernández" → "J. Pérez / L. Fernández": entra en los casilleros del cuadro. */
export function abreviarPareja(nombres: string | null): string {
  if (!nombres) return '';
  return nombres.split('/').map(jugador => abreviarJugador(jugador.trim())).join(' / ');
}

function abreviarJugador(nombre: string): string {
  const partes = nombre.split(/\s+/).filter(Boolean);
  return partes.length < 2 ? nombre : `${partes[0].charAt(0)}. ${partes.slice(1).join(' ')}`;
}

/** Los dos jugadores por separado, para mostrarlos en dos líneas. */
export function jugadoresDePareja(nombres: string | null): string[] {
  return (nombres ?? '').split('/').map(j => j.trim()).filter(Boolean);
}
