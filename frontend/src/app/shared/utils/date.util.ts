import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Cantidad de años a futuro que toleran los formularios con fecha (torneos,
 * bloqueos, bonos, solicitudes de partido). Debe coincidir con el límite del
 * backend (ver FechaHoraUtil.MAX_ANIOS_A_FUTURO) para que un valor aceptado acá
 * no rebote luego en el servidor. */
export const MAX_ANIOS_A_FUTURO = 5;

export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function toIsoDateTimeLocal(d: Date): string {
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${toIsoDate(d)}T${hh}:${mm}`;
}

export function todayIsoDate(): string {
  return toIsoDate(new Date());
}

export function maxIsoDate(aniosAFuturo: number = MAX_ANIOS_A_FUTURO): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() + aniosAFuturo);
  return toIsoDate(d);
}

export function nowIsoDateTimeLocal(): string {
  return toIsoDateTimeLocal(new Date());
}

export function maxIsoDateTimeLocal(aniosAFuturo: number = MAX_ANIOS_A_FUTURO): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() + aniosAFuturo);
  return toIsoDateTimeLocal(d);
}

/** Guarda contra años disparatados escritos a mano en un <input type="date"> (ej.
 * "20260-01-01" en vez de "2026-01-01"): HTML permite tipear más de 4 dígitos de
 * año antes de que el navegador termine de validar, así que se valida también acá. */
export function rangoDeFechaValidator(minIso: string, maxIso: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (!value) return null;
    const fecha = String(value).substring(0, 10);
    if (fecha < minIso || fecha > maxIso) {
      return { rangoFecha: { min: minIso, max: maxIso } };
    }
    return null;
  };
}
