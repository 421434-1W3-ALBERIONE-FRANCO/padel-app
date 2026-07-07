export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  wsUrl: 'ws://localhost:8080/ws',
  // Mock en localStorage para desarrollar/probar el frontend sin el backend levantado.
  // Se fuerza a false en environment.prod.ts (ver fileReplacements en angular.json).
  mockApiEnabled: true
};
