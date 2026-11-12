import { createHash, randomUUID } from 'node:crypto';
import type { Logger } from '../../application/port';
import type { ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import { DuplicateIdempotencyKeyError } from '../../domain/priceHistoryRepository';
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

    let inserted = 0;
    for (const { quote, identity } of quotes) {
      try {
        await deps.repository.append({ quote, idempotencyKey: identity, requestHash: identity, sourceIdentity: identity });
        inserted += 1;
        try {
          await deps.cache.putLatest(quote);
        } catch (err) {
          logger.warn('Redis update failed after VNSAT price write; MySQL remains the source of truth', {
            priceId: quote.id,
            error: errorMessage(err),
          });
        }
      } catch (err) {
        if (err instanceof DuplicateIdempotencyKeyError) {
          // A prior scrape may have committed MySQL but lost its Redis write before the process stopped.
          const latest = await deps.repository.findLatest(COMMODITY, quote.provinceSlug, quote.itemSlug);
          if (latest) {
            try {
              const cached = await deps.cache.getLatest(quote.provinceSlug, quote.itemSlug);
              if (cached?.id !== latest.id) await deps.cache.putLatest(latest);
            } catch (cacheErr) {
              logger.warn('Redis repair failed while replaying an existing VNSAT row', {
                priceId: latest.id,
                error: errorMessage(cacheErr),
              });
              try {
                await deps.cache.putLatest(latest);
              } catch (putErr) {
                logger.warn('Redis update failed after replaying an existing VNSAT row', {
                  priceId: latest.id,
                  error: errorMessage(putErr),
                });
              }
            }
          }
          continue;
        }
        throw err;
      }
    }
    logger.info('VNSAT coffee price ingestion completed', { rows: rows.length, inserted });
    return { rows: rows.length, inserted };
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

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
