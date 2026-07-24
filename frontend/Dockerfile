# ===== Etapa de build =====
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json .npmrc ./
RUN npm ci

COPY . .
RUN npm run build

# ===== Etapa de runtime (Nginx) =====
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# El builder 'application' de Angular 18 emite en dist/<proyecto>/browser
COPY --from=build /app/dist/padel-frontend/browser /usr/share/nginx/html

EXPOSE 80
