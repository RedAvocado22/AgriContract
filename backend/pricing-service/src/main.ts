import type { Logger, PriceCache } from './application/port.js';
import { createRedisCache, noCache } from './infrastructure/cache/redisPriceCache.js';
import { loadConfig } from './infrastructure/config.js';
import { createApp } from './infrastructure/http/app.js';
import { MysqlPriceHistoryRepository, createMysqlPool } from './infrastructure/persistence/mysqlPriceHistoryRepository.js';

const logger: Logger = {
  info: (m, meta) => console.info(JSON.stringify({ level: 'info', msg: m, ...meta })),
  warn: (m, meta) => console.warn(JSON.stringify({ level: 'warn', msg: m, ...meta })),
  error: (m, meta) => console.error(JSON.stringify({ level: 'error', msg: m, ...meta })),
};

/** Composition root: the only place that wires infrastructure adapters into the use case. */
function main(): void {
  const config = loadConfig();
  const pool = createMysqlPool(config.db);

  // Redis is optional: nothing here waits for it, so the service starts even when Redis is down.
  let cache: PriceCache = noCache;
  let closeCache: () => Promise<void> = async () => {};
  if (config.redis.url) {
    const redis = createRedisCache(
      {
        url: config.redis.url,
        connectTimeoutMs: config.redis.connectTimeoutMs,
        commandTimeoutMs: config.redis.commandTimeoutMs,
      },
      logger,
    );
    cache = redis.cache;
    closeCache = redis.close;
  } else {
    logger.warn('REDIS_URL not set: manual prices are stored in MySQL only (cache disabled)');
  }

  const server = createApp({
    repository: new MysqlPriceHistoryRepository(pool),
    cache,
    logger,
    gatewaySecret: config.gatewaySecret,
  });
  server.listen(config.port, () => logger.info('pricing-service listening', { port: config.port }));

  const shutdown = () => {
    server.close(() => {
      void Promise.allSettled([pool.end(), closeCache()]).finally(() => process.exit(0));
    });
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

try {
  main();
} catch (err) {
  logger.error('pricing-service failed to start', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
}
