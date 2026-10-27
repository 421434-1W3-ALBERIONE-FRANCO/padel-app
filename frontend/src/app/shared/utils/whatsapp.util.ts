/**
 * Link para escribirle por WhatsApp a un jugador con un mensaje ya cargado, o null si no tiene teléfono.
 * Los teléfonos se cargan sin código de país: si falta, se asume Argentina (+54).
 */
export function urlWhatsapp(telefono: string | null | undefined, mensaje: string): string | null {
  const digitos = (telefono ?? '').replace(/\D/g, '');
  if (!digitos) return null;
  const numero = digitos.startsWith('54') ? digitos : `54${digitos}`;
  return `https://wa.me/${numero}?text=${encodeURIComponent(mensaje)}`;
}
