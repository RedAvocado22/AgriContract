import mysql from 'mysql2/promise';
import { createClient } from 'redis';
import { RedisPriceCache, noCache, type PriceCache, type RedisClientLike } from './cache.js';
import { loadConfig } from './config.js';
import { createApp } from './http.js';
import { MysqlPriceHistoryRepository } from './repository.js';
import type { Logger } from './service.js';

const logger: Logger = {
  info: (m, meta) => console.info(JSON.stringify({ level: 'info', msg: m, ...meta })),
  warn: (m, meta) => console.warn(JSON.stringify({ level: 'warn', msg: m, ...meta })),
  error: (m, meta) => console.error(JSON.stringify({ level: 'error', msg: m, ...meta })),
};

async function main(): Promise<void> {
  const config = loadConfig();
  const pool = mysql.createPool({
    ...config.db,
    connectionLimit: 10,
    dateStrings: true, // DATE/DATETIME come back exactly as stored (UTC), no local-time shifting
    timezone: 'Z',
  });

  let cache: PriceCache = noCache;
  if (config.redisUrl) {
    const client = createClient({ url: config.redisUrl });
    client.on('error', (err: Error) => logger.warn('Redis error', { error: err.message }));
    await client.connect();
    cache = new RedisPriceCache(client as unknown as RedisClientLike);
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
      void pool.end().finally(() => process.exit(0));
    });
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((err) => {
  logger.error('pricing-service failed to start', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
});
