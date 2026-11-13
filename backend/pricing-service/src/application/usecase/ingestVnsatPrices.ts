import { createHash, randomUUID } from 'node:crypto';
import type { NewPriceRow, PriceHistoryRepository } from '../../domain/priceHistoryRepository';
import { COMMODITY, slugify, type PriceQuote } from '../../domain/priceQuote';
import type { Logger, PriceCache, ReferencePriceSource, ScrapedPrice } from '../port';

export interface IngestVnsatDeps {
  repository: PriceHistoryRepository;
  cache: PriceCache;
  logger: Logger;
  source: ReferencePriceSource;
  now?: () => Date;
  newId?: () => string;
}

export interface IngestResult {
  /** Rows the source returned (0 when the run failed). */
  rows: number;
  /** Rows newly committed; rows already stored are skipped. */
  inserted: number;
}

/** price_history.price is DECIMAL(10,2). */
const PRICE = /^\d{1,8}\.\d{2}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Use case "ingest VNSAT coffee prices" (design §3.1, §3.3, §4).
 *  1. Read the source, then check EVERY row before writing any: one bad row fails the whole run.
 *  2. A row's identity is commodity|province|item|capturedAt|price: the same published price is stored once.
 *  3. Write the batch in one transaction (all or nothing), then refresh Redis from MySQL after commit.
 *  4. Any failure is recorded in price_ingestion_failure and never thrown: stored and cached quotes stay,
 *     and Operators keep the manual fallback (design §3.3).
 */
export async function ingestVnsatPrices(deps: IngestVnsatDeps): Promise<IngestResult> {
  const { logger } = deps;
  try {
    const scraped = await deps.source.fetchCoffeePrices();
    const ingestedAt = (deps.now?.() ?? new Date()).toISOString();
    const newId = deps.newId ?? randomUUID;
    const batch = scraped.map((row, index) => toNewRow(checked(row, index), ingestedAt, newId(), deps.source.url));

    const inserted = await deps.repository.appendScrapedBatch(batch);

    // Redis only after commit. Refreshing each province/item from MySQL also repairs a cache entry that an
    // earlier run lost after its rows were committed.
    const keys = new Map(batch.map(({ quote }) => [`${quote.provinceSlug}|${quote.itemSlug}`, quote]));
    for (const { provinceSlug, itemSlug } of keys.values()) {
      await refreshCache(deps, provinceSlug, itemSlug);
    }
    logger.info('VNSAT coffee price ingestion completed', { rows: scraped.length, inserted: inserted.length });
    return { rows: scraped.length, inserted: inserted.length };
  } catch (err) {
    const reason = errorMessage(err);
    logger.error('VNSAT coffee price ingestion failed; existing prices were kept', { error: reason });
    try {
      await deps.repository.recordIngestionFailure?.(COMMODITY, reason);
    } catch (recordErr) {
      logger.error('Could not persist VNSAT ingestion failure', { error: errorMessage(recordErr) });
    }
    return { rows: 0, inserted: 0 };
  }
}

/** Rejects a row that would store a wrong or unreadable price (design §5: fail loudly, never store wrong data). */
function checked(row: ScrapedPrice, index: number): ScrapedPrice {
  const problems: string[] = [];
  if (!row.itemName?.trim()) problems.push('itemName is empty');
  if (!row.province?.trim()) problems.push('province is empty');
  if (!PRICE.test(row.price ?? '') || Number(row.price) <= 0) problems.push(`price "${row.price}" is not a positive VND/kg amount`);
  if (!isRealDate(row.capturedAt)) problems.push(`capturedAt "${row.capturedAt}" is not a date`);
  if (problems.length > 0) throw new Error(`source row ${index}: ${problems.join('; ')}`);
  return row;
}

function isRealDate(text: string | undefined): boolean {
  if (!text || !DATE.test(text)) return false;
  const parsed = new Date(`${text}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === text;
}

function toNewRow(row: ScrapedPrice, ingestedAt: string, id: string, sourceUrl: string): NewPriceRow {
  const itemSlug = slugify(row.itemName);
  const provinceSlug = slugify(row.province);
  const identity = createHash('sha256')
    .update(`${COMMODITY}|${provinceSlug}|${itemSlug}|${row.capturedAt}|${row.price}`)
    .digest('hex');
  const quote: PriceQuote = {
    id,
    commodity: COMMODITY,
    itemName: row.itemName,
    itemSlug,
    province: row.province,
    provinceSlug,
    price: row.price,
    source: 'VNSAT_SCRAPE',
    capturedAt: row.capturedAt,
    ingestedAt,
    referenceSource: sourceUrl,
    reason: null,
    recordedBy: null,
  };
  return { quote, idempotencyKey: identity, requestHash: identity, sourceIdentity: identity };
}

/** Puts the latest committed quote of one province/item into Redis. Redis errors are logged, never thrown. */
async function refreshCache(deps: IngestVnsatDeps, provinceSlug: string, itemSlug: string): Promise<void> {
  const latest = await deps.repository.findLatest(COMMODITY, provinceSlug, itemSlug);
  if (!latest) return;
  try {
    const cached = await deps.cache.getLatest(provinceSlug, itemSlug).catch(() => undefined);
    if (cached?.id !== latest.id) await deps.cache.putLatest(latest);
  } catch (err) {
    deps.logger.warn('Redis update failed after VNSAT price write; MySQL remains the source of truth', {
      priceId: latest.id,
      error: errorMessage(err),
    });
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
