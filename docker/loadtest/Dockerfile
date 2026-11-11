# Node image for seed / smoke / load test / PostgreSQL worker.
FROM node:20-bookworm-slim

ENV NODE_ENV=production \
    NODE_OPTIONS=--max-old-space-size=2048

WORKDIR /app

COPY loadtest/package.json loadtest/package-lock.json ./
RUN npm ci --omit=dev

COPY scripts ./scripts
COPY docker/postgres/init.sql ./docker/postgres/init.sql

RUN mkdir -p /app/loadtest/results

CMD ["node", "--version"]
