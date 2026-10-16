import { randomUUID } from 'node:crypto';
import { businessToday, requestHash, validateManualPrice } from '../../domain/manualPrice.js';
import {
  DuplicateIdempotencyKeyError,
  type PriceHistoryRepository,
  type StoredManualPrice,
} from '../../domain/priceHistoryRepository.js';
import { COMMODITY, slugify, type PriceQuote } from '../../domain/priceQuote.js';
import { IdempotencyConflict, IdempotencyKeyRequired, ValidationFailed } from '../errors.js';
import type { Actor, Logger, PriceCache } from '../port.js';

export interface ManualPriceDeps {
  repository: PriceHistoryRepository;
  cache: PriceCache;
  logger: Logger;
  now?: () => Date;
  newId?: () => string;
}

export interface ManualPriceResult {
  quote: PriceQuote;
  /** true when the same Idempotency-Key + body was already recorded: the original result is returned. */
  replayed: boolean;
}

const MAX_IDEMPOTENCY_KEY = 255;

/**
 * Admin/Operator manual fallback (design §3.2): validate, append ONE row to
 * price_history with source = OPERATOR_MANUAL, then refresh Redis after the write.
 * Never updates or deletes an older price.
 */
export async function recordManualPrice(
  deps: ManualPriceDeps,
  actor: Actor,
  idempotencyKey: string | undefined,
  body: unknown,
): Promise<ManualPriceResult> {
  const key = idempotencyKey?.trim();
  if (!key) throw new IdempotencyKeyRequired();
  if (key.length > MAX_IDEMPOTENCY_KEY) {
    throw new ValidationFailed([`Idempotency-Key must be at most ${MAX_IDEMPOTENCY_KEY} characters`]);
  }

  const now = deps.now?.() ?? new Date();
  const validation = validateManualPrice(body, businessToday(now));
  if (!validation.ok) throw new ValidationFailed(validation.errors);
  const input = validation.value;
  const hash = requestHash(input);

  const existing = await deps.repository.findByIdempotencyKey(actor.userId, key);
  if (existing) return replay(deps, existing, hash);

  const quote: PriceQuote = {
    id: deps.newId?.() ?? randomUUID(),
    commodity: COMMODITY,
    itemName: input.itemName,
    itemSlug: slugify(input.itemName),
    province: input.province,
    provinceSlug: slugify(input.province),
    price: input.price,
    source: 'OPERATOR_MANUAL',
    capturedAt: input.capturedAt,
    ingestedAt: now.toISOString(),
    referenceSource: input.referenceSource,
    reason: input.reason,
    recordedBy: actor.userId,
  };

  try {
    await deps.repository.append({ quote, idempotencyKey: key, requestHash: hash });
  } catch (err) {
    if (err instanceof DuplicateIdempotencyKeyError) {
      // Lost a race with a concurrent request using the same key.
      const winner = await deps.repository.findByIdempotencyKey(actor.userId, key);
      if (winner) return replay(deps, winner, hash);
    }
    throw err;
  }

  deps.logger.info('Manual reference price recorded', {
    priceId: quote.id,
    province: quote.province,
    itemSlug: quote.itemSlug,
    capturedAt: quote.capturedAt,
    recordedBy: actor.userId,
  });

  await refreshCache(deps, quote);
  return { quote, replayed: false };
}

/**
 * Same key + same body: return the stored result, and refresh the cache again. The first attempt
 * may have committed the row but failed to update Redis; without this, an older cached quote would
 * stay a cache hit forever and never self-heal. putLatest never replaces a newer cached quote, so
 * refreshing on every replay is safe.
 */
async function replay(deps: ManualPriceDeps, stored: StoredManualPrice, hash: string): Promise<ManualPriceResult> {
  if (stored.requestHash !== hash) throw new IdempotencyConflict();
  await refreshCache(deps, stored.quote);
  return { quote: stored.quote, replayed: true };
}

/**
 * After the MySQL write. A cache failure must not fail the request: MySQL is the source of truth.
 * The cache adapter bounds every call with a timeout, so this never hangs the request.
 */
async function refreshCache(deps: ManualPriceDeps, quote: PriceQuote): Promise<void> {
  try {
    await deps.cache.putLatest(quote);
  } catch (err) {
    deps.logger.warn('Redis update failed after manual price write; a retry with the same Idempotency-Key refreshes it', {
      priceId: quote.id,
      error: err instanceof Error ? err.message : String(err),
    });
  }
}
