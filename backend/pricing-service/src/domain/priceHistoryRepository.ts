import type { PriceQuote } from './priceQuote';

/** A row to append to price_history together with its idempotency identity. */
export interface NewPriceRow {
  quote: PriceQuote;
  idempotencyKey: string;
  requestHash: string;
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
  findByIdempotencyKey(recordedBy: string, idempotencyKey: string): Promise<StoredManualPrice | undefined>;
}

export class DuplicateIdempotencyKeyError extends Error {
  constructor() {
    super('Idempotency-Key already used by this actor');
    this.name = 'DuplicateIdempotencyKeyError';
  }
}
