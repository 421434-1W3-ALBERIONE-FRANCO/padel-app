// Desarrollo local: backend en el 8080 (mvn spring-boot:run -Dspring-boot.run.profiles=dev).
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  wsUrl: 'ws://localhost:8080/ws'
};
