// Producción: nginx sirve la SPA y hace de proxy a /api y /ws en el mismo origen (ver nginx.conf), así que las
// URLs son relativas y no hace falta CORS. El WebSocket usa wss cuando el sitio se sirve con TLS.
export const environment = {
  production: true,
  apiUrl: '/api/v1',
  wsUrl: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`
};
