// Produccion con frontend en Netlify (Netlify Drop) y backend en Render, en dominios
// distintos: por eso la API y el WebSocket apuntan a la URL ABSOLUTA de Render.
// (Si algun dia se sirve todo desde el mismo dominio detras de nginx, volver a rutas relativas.)
export const environment = {
  production: true,
  apiUrl: 'https://padel-app-web-service-back-end.onrender.com/api/v1',
  wsUrl: 'wss://padel-app-web-service-back-end.onrender.com/ws',
  mockApiEnabled: false
};
