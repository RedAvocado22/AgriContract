import { COMMODITY, isSlug, type PriceQuote } from '../../domain/priceQuote';
import type { PriceHistoryRepository } from '../../domain/priceHistoryRepository';
import { isStale } from '../../domain/staleness';
import { PriceNotFound } from '../errors';
import type { Logger, PriceCache } from '../port';

export interface PriceReadDeps {
  repository: PriceHistoryRepository;
  cache: PriceCache;
  logger: Logger;
  /** A quote older than this many days (Vietnam date) is returned with stale = true (design §2). */
  staleThresholdDays: number;
  now?: () => Date;
}

/** The public advisory quote (design §4): the stored quote without who recorded it, plus a computed stale flag. */
export type PublicPriceQuote = Omit<PriceQuote, 'recordedBy'> & { stale: boolean };

/**
 * Use case "latest reference price for a province" (design §4, contract getPriceQuote).
 *  1. Only COFFEE and well-formed slugs can name a quote; anything else is PriceNotFound.
 *  2. Without ?item=, the item is inferred when the province has exactly one; several -> PriceNotFound.
 *  3. Cache-aside: Redis first; on a miss or a Redis error read MySQL (source of truth) and repair Redis.
 *  4. stale is computed at read time, never stored.
 */
export async function getPriceQuote(
  deps: PriceReadDeps,
  commodity: string,
  provinceSlug: string,
  requestedItem?: string,
): Promise<PublicPriceQuote> {
  if (commodity !== COMMODITY || !isSlug(provinceSlug) || (requestedItem !== undefined && !isSlug(requestedItem))) {
    throw new PriceNotFound();
  }
  const itemSlug = requestedItem ?? (await inferItem(deps, provinceSlug));
  const quote = await readThroughCache(deps, provinceSlug, itemSlug);
  // Fields listed one by one: a cached value can never leak recordedBy or an unexpected key.
  return {
    id: quote.id,
    commodity: quote.commodity,
    itemName: quote.itemName,
    itemSlug: quote.itemSlug,
    province: quote.province,
    provinceSlug: quote.provinceSlug,
    price: quote.price,
    source: quote.source,
    capturedAt: quote.capturedAt,
    ingestedAt: quote.ingestedAt,
    referenceSource: quote.referenceSource,
    reason: quote.reason,
    stale: isStale(quote.capturedAt, deps.now?.() ?? new Date(), deps.staleThresholdDays),
  };
}

async function inferItem(deps: PriceReadDeps, provinceSlug: string): Promise<string> {
  const available = await deps.repository.listItems(COMMODITY, provinceSlug);
  if (available.length === 0) throw new PriceNotFound();
  if (available.length > 1) {
    throw new PriceNotFound('Several items have prices for this province; pass ?item= (see /items)');
  }
  return available[0]!.itemSlug;
}

async function readThroughCache(deps: PriceReadDeps, provinceSlug: string, itemSlug: string): Promise<PriceQuote> {
  try {
    const cached = await deps.cache.getLatest(provinceSlug, itemSlug);
    if (cached) return cached;
  } catch (err) {
    deps.logger.warn('Redis read failed; using MySQL price history', { provinceSlug, itemSlug, error: errorMessage(err) });
  }
  const stored = await deps.repository.findLatest(COMMODITY, provinceSlug, itemSlug);
  if (!stored) throw new PriceNotFound();
  try {
    await deps.cache.putLatest(stored);
  } catch (err) {
    deps.logger.warn('Redis cache repair failed; MySQL price history remains available', {
      priceId: stored.id,
      error: errorMessage(err),
    });
  }
  return stored;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
