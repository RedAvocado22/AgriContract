import { DynamicModule, Inject, Injectable, Module, type OnApplicationShutdown, type Provider } from '@nestjs/common';
import { APP_FILTER } from '@nestjs/core';
import type { Logger } from '../../application/port';
import type { ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import { createRedisCache, noCache, type RedisCacheHandle } from '../cache/redisPriceCache';
import { loadConfig, type Config } from '../config';
import { HealthController } from '../http/health.controller';
import { ManualPriceGuard } from '../http/manualPrice.guard';
import { PricesController } from '../http/prices.controller';
import { ProblemFilter } from '../http/problem.filter';
import { jsonLogger } from '../logger';
import { MysqlPriceHistoryRepository, createMysqlPool, type MysqlPoolLike } from '../persistence/mysqlPriceHistoryRepository';
import { CACHE_HANDLE, CONFIG, GATEWAY_SECRET, LOGGER, MANUAL_PRICE_DEPS, MYSQL_POOL } from './tokens';

export interface AppDeps extends ManualPriceDeps {
  gatewaySecret: string;
}

/** Closes the MySQL pool and the Redis connection when Nest shuts down (SIGTERM / SIGINT / app.close()). */
@Injectable()
export class PricingShutdown implements OnApplicationShutdown {
  constructor(
    @Inject(MYSQL_POOL) private readonly pool: { end(): Promise<void> },
    @Inject(CACHE_HANDLE) private readonly cache: RedisCacheHandle,
  ) {}

  async onApplicationShutdown(): Promise<void> {
    await Promise.allSettled([this.pool.end(), this.cache.close()]);
  }
}

const httpParts = {
  controllers: [PricesController, HealthController],
  providers: [ManualPriceGuard, { provide: APP_FILTER, useClass: ProblemFilter }] as Provider[],
};

/**
 * The whole service: HTTP controllers, guard and error filter, plus the adapters the use case needs.
 * `forRoot()` builds the adapters from the environment (production); `forDeps()` takes ready-made ones
 * (tests). Nothing here waits for MySQL or Redis, so the service starts, and /health answers, even when
 * they are down.
 */
@Module({})
export class PricingModule {
  static forRoot(): DynamicModule {
    return {
      module: PricingModule,
      controllers: httpParts.controllers,
      providers: [
        ...httpParts.providers,
        { provide: CONFIG, useFactory: (): Config => loadConfig() },
        { provide: LOGGER, useValue: jsonLogger },
        { provide: GATEWAY_SECRET, useFactory: (config: Config) => config.gatewaySecret, inject: [CONFIG] },
        { provide: MYSQL_POOL, useFactory: (config: Config) => createMysqlPool(config.db), inject: [CONFIG] },
        {
          // Redis is optional: connecting happens in the background and never blocks startup.
          provide: CACHE_HANDLE,
          useFactory: (config: Config, logger: Logger): RedisCacheHandle => {
            if (!config.redis.url) {
              logger.warn('REDIS_URL not set: manual prices are stored in MySQL only (cache disabled)');
              return { cache: noCache, close: async () => {} };
            }
            return createRedisCache(
              {
                url: config.redis.url,
                connectTimeoutMs: config.redis.connectTimeoutMs,
                commandTimeoutMs: config.redis.commandTimeoutMs,
              },
              logger,
            );
          },
          inject: [CONFIG, LOGGER],
        },
        {
          provide: MANUAL_PRICE_DEPS,
          useFactory: (pool: MysqlPoolLike, cache: RedisCacheHandle, logger: Logger): ManualPriceDeps => ({
            repository: new MysqlPriceHistoryRepository(pool),
            cache: cache.cache,
            logger,
          }),
          inject: [MYSQL_POOL, CACHE_HANDLE, LOGGER],
        },
        PricingShutdown,
      ],
    };
  }

  static forDeps(deps: AppDeps): DynamicModule {
    return {
      module: PricingModule,
      controllers: httpParts.controllers,
      providers: [
        ...httpParts.providers,
        { provide: LOGGER, useValue: deps.logger },
        { provide: GATEWAY_SECRET, useValue: deps.gatewaySecret },
        { provide: MANUAL_PRICE_DEPS, useValue: deps },
      ],
    };
  }
}
