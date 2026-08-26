# --- Stage 1: Bygg klient og BFF ---
# Chainguard-image via Navs pull-through, samme registry som backend bruker.
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:latest-dev AS builder
WORKDIR /app

ENV PNPM_HOME=/usr/local/bin
RUN corepack enable && corepack prepare pnpm@11.20.0 --activate

# Manifestene først: da gjenbrukes avhengighetslaget når bare kildekoden endres.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY client/package.json ./client/
COPY server/package.json ./server/
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# Bare produksjonsavhengigheter skal være med videre.
RUN pnpm --filter ./server deploy --prod --legacy /app/bundle \
    && cp -r /app/client/dist /app/bundle/client-dist

# --- Stage 2: Runtime ---
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:latest
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    STATIC_DIR=/app/client-dist

COPY --from=builder /app/bundle ./

EXPOSE 3000
CMD ["dist/index.js"]
