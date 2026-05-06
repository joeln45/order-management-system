# syntax=docker/dockerfile:1.7

# Stage 1: install deps against the lockfile.
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# Stage 2: build a Next.js standalone bundle.
# Requires `output: "standalone"` in next.config.ts so we can copy just
# the server + required node_modules into the runtime image.
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_TELEMETRY_DISABLED keeps the build logs clean in CI.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# Stage 3: tiny runtime. Non-root, no package manager.
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# Standalone output bundles the minimal server + only the deps it imports.
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nextjs /app/public ./public

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
