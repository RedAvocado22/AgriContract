import type { PriceQuote } from '../domain/priceQuote';

/** Authenticated caller. The web layer resolves it from the API gateway headers. */
export interface Actor {
  userId: string;
  roles: string[];
}

/**
 * Cache of the latest quote per province/item. Redis is only a cache; MySQL price_history is the
 * source of truth (design §2, §4). Implementations must fail fast instead of hanging (rules §4B).
 */
export interface PriceCache {
  /** Stores `quote` as the latest price for its province/item unless a newer one is already cached. */
  putLatest(quote: PriceQuote): Promise<void>;
}

export interface Logger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
}
