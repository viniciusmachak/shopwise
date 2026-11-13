# syntax=docker/dockerfile:1
# Web image: Caddy serves the built frontend and proxies the API, all on one domain,
# so the session and CSRF cookies are first-party. Build from the repository root:
#   docker build -f deploy/web.Dockerfile .

FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
