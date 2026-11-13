import { createHash, randomUUID } from 'node:crypto';
import type { Logger } from '../../application/port';
import type { ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import { COMMODITY, slugify, type PriceQuote } from '../../domain/priceQuote';
import type { VnsatRow } from './vnsatTable';
import { toVnsatPrice } from './vnsatPrice';

/** Testable ingestion boundary: validate the whole batch before append, then cache only committed rows. */
export async function runVnsatPriceIngestion(
  deps: ManualPriceDeps,
  logger: Logger,
  scrape: () => Promise<VnsatRow[]>,
  now = new Date(),
  newId = randomUUID,
): Promise<{ rows: number; inserted: number }> {
  try {
    const rows = await scrape();
    const ingestedAt = now.toISOString();
    // Convert/validate every row before inserting any, so a value/layout change cannot create a partial batch.
    const quotes = rows.map((row): { quote: PriceQuote; identity: string } => {
      const mapped = toVnsatPrice(row);
      const itemSlug = slugify(mapped.itemName);
      const provinceSlug = slugify(mapped.province);
      const identity = createHash('sha256')
        .update(`${COMMODITY}|${provinceSlug}|${itemSlug}|${mapped.capturedAt}|${mapped.price}`)
        .digest('hex');
      return {
        identity,
        quote: {
          id: newId(),
          commodity: COMMODITY,
          itemName: mapped.itemName,
          itemSlug,
          province: mapped.province,
          provinceSlug,
          price: mapped.price,
          source: 'VNSAT_SCRAPE',
          capturedAt: mapped.capturedAt,
          ingestedAt,
          referenceSource: 'https://thitruongnongsan.gov.vn/vn/nguonwmy.aspx',
          reason: null,
          recordedBy: null,
        },
      };
    });

    // One transaction for the whole batch (design §4): a DB error leaves none of it committed.
    // Rows already stored (same sourceIdentity) are skipped, so the next run fills any gap.
    const inserted = await deps.repository.appendScrapedBatch(
      quotes.map(({ quote, identity }) => ({ quote, idempotencyKey: identity, requestHash: identity, sourceIdentity: identity })),
    );

    // Redis only after commit. Refresh every province/item of the batch from MySQL, which also repairs a
    // cache write lost by an earlier run whose rows were committed.
    const keys = new Map(quotes.map(({ quote }) => [`${quote.provinceSlug}|${quote.itemSlug}`, quote]));
    for (const { provinceSlug, itemSlug } of keys.values()) {
      await refreshCache(deps, logger, provinceSlug, itemSlug);
    }
    logger.info('VNSAT coffee price ingestion completed', { rows: rows.length, inserted: inserted.length });
    return { rows: rows.length, inserted: inserted.length };
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

/** Puts the latest committed quote of one province/item into Redis. Redis errors are logged, never thrown. */
async function refreshCache(deps: ManualPriceDeps, logger: Logger, provinceSlug: string, itemSlug: string): Promise<void> {
  const latest = await deps.repository.findLatest(COMMODITY, provinceSlug, itemSlug);
  if (!latest) return;
  try {
    const cached = await deps.cache.getLatest(provinceSlug, itemSlug).catch(() => undefined);
    if (cached?.id !== latest.id) await deps.cache.putLatest(latest);
  } catch (err) {
    logger.warn('Redis update failed after VNSAT price write; MySQL remains the source of truth', {
      priceId: latest.id,
      error: errorMessage(err),
    });
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
