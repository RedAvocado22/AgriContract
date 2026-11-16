FROM node:22-alpine AS builder
WORKDIR /app/file-service
COPY file-service/package.json file-service/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY file-service/ ./
RUN npm run build && npm prune --omit=dev --no-audit --no-fund

FROM node:22-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app/file-service
COPY --from=builder /app/file-service/package.json ./package.json
COPY --from=builder /app/file-service/node_modules ./node_modules
COPY --from=builder /app/file-service/dist ./dist
USER node
EXPOSE 8089
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:' + (process.env.SERVER_PORT || 8089) + '/actuator/health').then((r) => process.exit(r.status === 200 ? 0 : 1)).catch(() => process.exit(1))"]
CMD ["node", "dist/main.js"]
