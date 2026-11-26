# syntax=docker/dockerfile:1.7
# Multi-stage build: Node compiles the SPA, a tiny Caddy image serves the static files.

# ---------- 1. build ----------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Vite inlines VITE_* variables at build time
ARG VITE_API_BASE_URL=""
ARG VITE_KIBANA_URL="/kibana/app/dashboards#/view/transactions-overview"
ARG VITE_DEMO_MODE="true"
ARG VITE_DEMO_CUSTOMER_PASSWORD="Customer123!"
ARG VITE_DEMO_ADMIN_PASSWORD="Admin123!"
RUN npm run build

# ---------- 2. serve ----------
FROM caddy:2.10-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost/healthz || exit 1
