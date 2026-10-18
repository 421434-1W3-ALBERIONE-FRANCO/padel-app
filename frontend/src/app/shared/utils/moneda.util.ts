/**
 * "$ 12.000" o "$ 1.234,50" con el formato argentino. Usa el Intl del navegador porque la app no registra
 * el locale es-AR en Angular (el pipe number formatea en inglés).
 */
export function formatearPesos(valor: number | null | undefined): string {
  const numero = Number(valor ?? 0);
  const decimales = Number.isInteger(numero) ? 0 : 2;
  return `$ ${numero.toLocaleString('es-AR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales })}`;
}
