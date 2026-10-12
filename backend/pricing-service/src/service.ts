import { randomUUID } from 'node:crypto';
import type { Actor } from './auth.js';
import type { PriceCache } from './cache.js';
import {
  COMMODITY,
  businessToday,
  requestHash,
  slugify,
  validateManualPrice,
  type PriceQuote,
} from './domain.js';
import { HttpError } from './errors.js';
import { DuplicateIdempotencyKeyError, type PriceHistoryRepository } from './repository.js';

export interface Logger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
}

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
 * price_history with source = ADMIN_MANUAL, then refresh Redis after the write.
 * Never updates or deletes an older price.
 */
export async function recordManualPrice(
  deps: ManualPriceDeps,
  actor: Actor,
  idempotencyKey: string | undefined,
  body: unknown,
): Promise<ManualPriceResult> {
  const key = idempotencyKey?.trim();
  if (!key) throw new HttpError(400, 'IDEMPOTENCY_KEY_REQUIRED', 'Idempotency-Key header is required');
  if (key.length > MAX_IDEMPOTENCY_KEY) {
    throw new HttpError(400, 'VALIDATION_FAILED', `Idempotency-Key must be at most ${MAX_IDEMPOTENCY_KEY} characters`);
  }

  const now = deps.now?.() ?? new Date();
  const validation = validateManualPrice(body, businessToday(now));
  if (!validation.ok) throw new HttpError(400, 'VALIDATION_FAILED', validation.errors.join('; '));
  const input = validation.value;
  const hash = requestHash(input);

  const existing = await deps.repository.findByIdempotencyKey(actor.userId, key);
  if (existing) return replayOrConflict(existing.quote, existing.requestHash, hash);

  const quote: PriceQuote = {
    id: deps.newId?.() ?? randomUUID(),
    commodity: COMMODITY,
    itemName: input.itemName,
    itemSlug: slugify(input.itemName),
    province: input.province,
    provinceSlug: slugify(input.province),
    price: input.price,
    source: 'ADMIN_MANUAL',
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
      if (winner) return replayOrConflict(winner.quote, winner.requestHash, hash);
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

  // After the MySQL write. A cache failure must not fail the request: MySQL is the
  // source of truth and the read path re-fills Redis on a miss (cache-aside).
  try {
    await deps.cache.putLatest(quote);
  } catch (err) {
    deps.logger.warn('Redis update failed after manual price insert; cache will self-heal on read', {
      priceId: quote.id,
      error: err instanceof Error ? err.message : String(err),
    });
  }

  return { quote, replayed: false };
}

function replayOrConflict(quote: PriceQuote, storedHash: string, hash: string): ManualPriceResult {
  if (storedHash === hash) return { quote, replayed: true };
  throw new HttpError(
    409,
    'IDEMPOTENCY_KEY_STATE_CONFLICT',
    'This Idempotency-Key was already used with a different request body',
  );
}
