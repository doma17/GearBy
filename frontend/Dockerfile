FROM node:24-alpine AS build

WORKDIR /app
RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY frontend/package.json frontend/package.json
RUN pnpm install --frozen-lockfile --filter @gearby/frontend

COPY frontend frontend

ARG API_BASE_URL=http://localhost:8080
ARG NAVER_MAP_CLIENT_ID
ENV API_BASE_URL=$API_BASE_URL
ENV NAVER_MAP_CLIENT_ID=$NAVER_MAP_CLIENT_ID
RUN pnpm --dir frontend build

FROM node:24-alpine

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

COPY --from=build /app/frontend/.next/standalone ./
COPY --from=build /app/frontend/.next/static ./frontend/.next/static

EXPOSE 3000
CMD ["node", "frontend/server.js"]
