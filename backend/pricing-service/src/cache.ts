import { cacheKey, type PriceQuote } from './domain.js';

/**
 * Redis is only a cache; MySQL price_history is the source of truth (design §2, §4).
 * Keys have no TTL on purpose; staleness is computed from capturedAt at read time.
 */
export interface PriceCache {
  /** Stores `quote` as the latest price for its province/item unless a newer one is already cached. */
  putLatest(quote: PriceQuote): Promise<void>;
}

/** Subset of a node-redis v4 client. */
export interface RedisClientLike {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<unknown>;
}

export class RedisPriceCache implements PriceCache {
  constructor(private readonly client: RedisClientLike) {}

  async putLatest(quote: PriceQuote): Promise<void> {
    const key = cacheKey(quote.provinceSlug, quote.itemSlug);
    const current = await this.client.get(key);
    if (current) {
      try {
        const cached = JSON.parse(current) as Pick<PriceQuote, 'capturedAt' | 'ingestedAt'>;
        // A back-dated manual correction must not hide a price that applies to a later date.
        if (isNewer(cached, quote)) return;
      } catch {
        // unreadable cache entry: overwrite it
      }
    }
    await this.client.set(key, JSON.stringify(quote));
  }
}

/** No-op cache, used when REDIS_URL is not configured. */
export const noCache: PriceCache = {
  async putLatest() {},
};

function isNewer(
  a: Pick<PriceQuote, 'capturedAt' | 'ingestedAt'>,
  b: Pick<PriceQuote, 'capturedAt' | 'ingestedAt'>,
): boolean {
  if (a.capturedAt !== b.capturedAt) return a.capturedAt > b.capturedAt;
  return a.ingestedAt > b.ingestedAt;
}
