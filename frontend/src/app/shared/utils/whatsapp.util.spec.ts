import { urlWhatsapp } from './whatsapp.util';

describe('urlWhatsapp', () => {
  it('deja solo los dígitos y antepone el 54 cuando falta', () => {
    expect(urlWhatsapp('(351) 555-0101', 'Hola')).toBe('https://wa.me/543515550101?text=Hola');
  });

  it('no repite el 54 si el número ya lo trae', () => {
    expect(urlWhatsapp('+54 9 351 555 0101', 'Hola')).toBe('https://wa.me/5493515550101?text=Hola');
  });

  it('codifica el mensaje', () => {
    expect(urlWhatsapp('3515550101', '¡Hola Juan! ¿Jugamos?'))
      .toBe(`https://wa.me/543515550101?text=${encodeURIComponent('¡Hola Juan! ¿Jugamos?')}`);
  });

  it('sin teléfono no hay link', () => {
    expect(urlWhatsapp(null, 'Hola')).toBeNull();
    expect(urlWhatsapp(undefined, 'Hola')).toBeNull();
    expect(urlWhatsapp('', 'Hola')).toBeNull();
    expect(urlWhatsapp('sin número', 'Hola')).toBeNull();
  });
});
