# ===== Etapa de build =====
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ===== Etapa de runtime (nginx sin root: escucha en 8080) =====
# Tag "stable" a propósito: cada rebuild toma los parches de seguridad de la rama estable de nginx.
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# El builder 'application' de Angular emite en dist/<proyecto>/browser
COPY --from=build /app/dist/padel-frontend/browser /usr/share/nginx/html

EXPOSE 8080
