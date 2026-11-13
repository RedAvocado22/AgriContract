import type { PriceQuote } from './priceQuote';

/** A row to append to price_history together with its idempotency identity. */
export interface NewPriceRow {
  quote: PriceQuote;
  idempotencyKey: string;
  requestHash: string;
  /** Stable hash for a VNSAT row (source/province/item/captured date); null for operator entries. */
  sourceIdentity?: string | null;
}

export interface StoredManualPrice {
  quote: PriceQuote;
  requestHash: string;
}

/**
 * price_history is append-only: this port deliberately has no update/delete.
 */
export interface PriceHistoryRepository {
  /** Inserts the row. Throws DuplicateIdempotencyKeyError if (recordedBy, idempotencyKey) already exists. */
  append(row: NewPriceRow): Promise<void>;
  /**
   * Appends a scraped batch in ONE transaction (design §4): either every new row commits or none does.
   * A row whose sourceIdentity is already stored is skipped. Returns the quotes that were inserted.
   */
  appendScrapedBatch(rows: NewPriceRow[]): Promise<PriceQuote[]>;
  findByIdempotencyKey(recordedBy: string, idempotencyKey: string): Promise<StoredManualPrice | undefined>;
  findLatest(commodity: string, provinceSlug: string, itemSlug?: string): Promise<PriceQuote | undefined>;
  listItems(commodity: string, provinceSlug: string): Promise<Array<{ itemName: string; itemSlug: string }>>;
  recordIngestionFailure?(commodity: string, reason: string): Promise<void>;
}

export class DuplicateIdempotencyKeyError extends Error {
  constructor() {
    super('Idempotency-Key already used by this actor');
    this.name = 'DuplicateIdempotencyKeyError';
  }
}
