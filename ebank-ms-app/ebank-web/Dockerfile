# ==============================================================================
#  ebank-web - React (Vite) client served by Nginx
#
#  Build stage : npm ci + vite build (static files in /app/dist)
#  Runtime     : Nginx on port 8080, serves the SPA and proxies /api/ to the
#                gateway (http://gateway-service:9999) on the compose network.
#
#  docker build -t ebank-ms/ebank-web:local ebank-web
# ==============================================================================
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first: this layer is cached as long as package*.json is unchanged
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime

# Unprivileged port so the container can run with cap_drop: ALL (prod overlay)
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=10s \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
