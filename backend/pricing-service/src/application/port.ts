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
  /** Reads the cached latest quote, if Redis has one for this province/item. */
  getLatest(provinceSlug: string, itemSlug: string): Promise<PriceQuote | undefined>;
  /** Stores `quote` as the latest price for its province/item unless a newer one is already cached. */
  putLatest(quote: PriceQuote): Promise<void>;
}

/** One coffee price row read from the external reference source (design §3.1), in price_history formats. */
export interface ScrapedPrice {
  /** Source item name, e.g. "Cà phê Robusta nhân xô". */
  itemName: string;
  /** Source province, e.g. "Đắk Lắk". */
  province: string;
  /** VND/kg with 2 decimals, e.g. "87333.00". */
  price: string;
  /** Date the price applies (Vietnam calendar date), YYYY-MM-DD. */
  capturedAt: string;
}

/**
 * The automated reference-price source. Today the VNSAT scraper; the use case does not know how the
 * rows are fetched. Throws when the source cannot be read or no longer looks as expected.
 */
export interface ReferencePriceSource {
  /** Stored as referenceSource on every row from this source. */
  readonly url: string;
  fetchCoffeePrices(): Promise<ScrapedPrice[]>;
}

export interface Logger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
}
