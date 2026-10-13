import { createClient } from 'redis';
import type { Logger, PriceCache } from '../../application/port.js';
import type { PriceQuote } from '../../domain/priceQuote.js';

/** Redis key from design §2: price:coffee:{province}:{itemSlug}. */
export function cacheKey(provinceSlug: string, itemSlug: string): string {
  return `price:coffee:${provinceSlug}:${itemSlug}`;
}

/** Subset of a node-redis v4 client. */
export interface RedisClientLike {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<unknown>;
}

export class CacheTimeoutError extends Error {
  constructor(ms: number) {
    super(`Redis did not answer within ${ms} ms`);
    this.name = 'CacheTimeoutError';
  }
}

/**
 * Redis is only a cache; MySQL price_history is the source of truth (design §2, §4).
 * Keys have no TTL on purpose; staleness is computed from capturedAt at read time.
 * Every update has a deadline, so a slow or silent Redis can never hang a request (rules §4B).
 */
export class RedisPriceCache implements PriceCache {
  constructor(
    private readonly client: RedisClientLike,
    private readonly timeoutMs: number,
  ) {}

  putLatest(quote: PriceQuote): Promise<void> {
    return withTimeout(this.write(quote), this.timeoutMs);
  }

  private async write(quote: PriceQuote): Promise<void> {
    const key = cacheKey(quote.provinceSlug, quote.itemSlug);
    const cached = readCachedOrder(await this.client.get(key));
    // A back-dated manual correction must not hide a price that applies to a later date.
    if (cached && isNewer(cached, quote)) return;
    await this.client.set(key, JSON.stringify(quote));
  }
}

/** No-op cache, used when REDIS_URL is not configured. */
export const noCache: PriceCache = {
  async putLatest() {},
};

export interface RedisCacheOptions {
  url: string;
  connectTimeoutMs: number;
  commandTimeoutMs: number;
}

export interface RedisCacheHandle {
  cache: PriceCache;
  close(): Promise<void>;
}

/**
 * Redis is an optional dependency (rules §4B): it must never block startup or a request.
 * - connect() runs in the background; the service starts even when Redis is down.
 * - disableOfflineQueue: while disconnected, commands fail at once instead of queueing forever.
 * - reconnects in the background with capped backoff.
 * - each cache update has a deadline (commandTimeoutMs), which also covers a Redis that accepts
 *   the TCP connection but stops answering.
 */
export function createRedisCache(options: RedisCacheOptions, logger: Logger): RedisCacheHandle {
  const client = createClient({
    url: options.url,
    disableOfflineQueue: true,
    socket: {
      connectTimeout: options.connectTimeoutMs,
      reconnectStrategy: (retries: number) => Math.min(100 * 2 ** retries, 5000),
    },
  });
  client.on('error', (err: Error) => logger.warn('Redis error', { error: err.message }));
  client.on('ready', () => logger.info('Redis connected'));
  client.connect().catch((err: unknown) => {
    logger.warn('Redis unavailable: continuing with MySQL only', {
      error: err instanceof Error ? err.message : String(err),
    });
  });
  return {
    cache: new RedisPriceCache(client as unknown as RedisClientLike, options.commandTimeoutMs),
    close: async () => {
      if (client.isOpen) await client.disconnect();
    },
  };
}

type CachedOrder = Pick<PriceQuote, 'capturedAt' | 'ingestedAt'>;

const CACHED_DATE = /^\d{4}-\d{2}-\d{2}$/;
const CACHED_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

/** Reads the ordering fields of the cached entry; anything unreadable or malformed is overwritten. */
function readCachedOrder(raw: string | null): CachedOrder | undefined {
  if (!raw) return undefined;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (typeof value !== 'object' || value === null) return undefined;
  const { capturedAt, ingestedAt } = value as Record<string, unknown>;
  if (typeof capturedAt !== 'string' || !CACHED_DATE.test(capturedAt)) return undefined;
  if (typeof ingestedAt !== 'string' || !CACHED_INSTANT.test(ingestedAt)) return undefined;
  return { capturedAt, ingestedAt };
}

function isNewer(a: CachedOrder, b: CachedOrder): boolean {
  if (a.capturedAt !== b.capturedAt) return a.capturedAt > b.capturedAt;
  return a.ingestedAt > b.ingestedAt;
}

function withTimeout<T>(work: Promise<T>, ms: number): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const deadline = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new CacheTimeoutError(ms)), ms);
  });
  return Promise.race([work, deadline]).finally(() => clearTimeout(timer));
}
