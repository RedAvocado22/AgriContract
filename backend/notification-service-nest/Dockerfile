FROM node:22-alpine AS builder
WORKDIR /app/shared/node-eventing
COPY shared/node-eventing/package.json shared/node-eventing/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY shared/node-eventing/ ./
RUN npm run build

WORKDIR /app/notification-service-nest
COPY notification-service-nest/package.json notification-service-nest/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY notification-service-nest/ ./
RUN npm run build && npm prune --omit=dev --no-audit --no-fund

FROM node:22-alpine AS runtime
ENV NODE_ENV=production
# Same layout as the builder: node_modules/@agricontract/eventing is a relative
# symlink to ../shared/node-eventing, which has no runtime dependencies of its own.
WORKDIR /app/notification-service-nest
COPY --from=builder /app/shared/node-eventing/package.json /app/shared/node-eventing/package.json
COPY --from=builder /app/shared/node-eventing/dist /app/shared/node-eventing/dist
COPY --from=builder /app/notification-service-nest/package.json ./package.json
COPY --from=builder /app/notification-service-nest/node_modules ./node_modules
COPY --from=builder /app/notification-service-nest/dist ./dist
USER node
EXPOSE 8085
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:' + (process.env.SERVER_PORT || 8085) + '/actuator/health').then((r) => process.exit(r.status === 200 ? 0 : 1)).catch(() => process.exit(1))"]
CMD ["node", "dist/main.js"]
