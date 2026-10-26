import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controlador: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('padel_token', 'token-de-prueba');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    controlador = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controlador.verify();
    localStorage.removeItem('padel_token');
  });

  it('agrega el token a los pedidos de la API', () => {
    http.get(`${environment.apiUrl}/usuarios/me`).subscribe();

    const pedido = controlador.expectOne(`${environment.apiUrl}/usuarios/me`);
    expect(pedido.request.headers.get('Authorization')).toBe('Bearer token-de-prueba');
    pedido.flush({});
  });

  it('no manda el token a otros orígenes', () => {
    http.get('https://fonts.googleapis.com/css2?family=Outfit').subscribe();

    const pedido = controlador.expectOne(r => r.url.startsWith('https://fonts.googleapis.com'));
    expect(pedido.request.headers.has('Authorization')).toBeFalse();
    pedido.flush('');
  });

  it('no se confunde con una URL que solo empieza igual que la API', () => {
    const parecida = `${environment.apiUrl}.example.com/robar`;
    http.get(parecida).subscribe();

    const pedido = controlador.expectOne(parecida);
    expect(pedido.request.headers.has('Authorization')).toBeFalse();
    pedido.flush({});
  });
});
