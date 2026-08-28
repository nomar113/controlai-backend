# syntax=docker/dockerfile:1

# --- Build stage: compila TypeScript ---
FROM node:20-slim AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

# --- Runtime stage: imagem oficial do Playwright ja traz o Chromium e as
# dependencias de sistema exigidas por ele, evitando instalar dezenas de libs
# manualmente via apt. A versao da tag deve acompanhar a versao resolvida de
# `playwright` (package-lock.json) — como a dependencia usa `^`, o valor
# instalado pode ficar a frente do que está fixado aqui; por isso o passo
# `playwright install chromium` abaixo sempre baixa o binario correto,
# mesmo que a tag desatualize com o tempo. ---
FROM mcr.microsoft.com/playwright:v1.62.1-jammy AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY package.json package-lock.json ./
RUN npm ci --omit=dev \
    && npx playwright install chromium

COPY --from=builder /app/dist ./dist

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://127.0.0.1:' + (process.env.PORT || 3000) + '/health', (r) => process.exit(r.statusCode === 200 ? 0 : 1)).on('error', () => process.exit(1))"

CMD ["node", "dist/index.js"]
