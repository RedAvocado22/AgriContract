import mysql from 'mysql2/promise';
import {
  DuplicateIdempotencyKeyError,
  type NewPriceRow,
  type PriceHistoryRepository,
  type StoredManualPrice,
} from '../../domain/priceHistoryRepository.js';
import type { PriceQuote } from '../../domain/priceQuote.js';

/** Subset of a mysql2/promise Pool (structural, so tests can fake it). */
export interface MysqlPoolLike {
  query(sql: string, values?: unknown[]): Promise<[unknown, unknown]>;
}

/**
 * The one place pool options are set, shared by main.ts and the MySQL integration test so the
 * test exercises the same type mapping as production.
 */
export function createMysqlPool(options: mysql.PoolOptions): mysql.Pool {
  return mysql.createPool({
    connectionLimit: 10,
    ...options,
    dateStrings: true, // DATE/DATETIME come back exactly as stored (UTC), no local-time shifting
    timezone: 'Z',
  });
}

interface PriceRow {
  id: string;
  commodity: string;
  item_name: string;
  item_slug: string;
  province: string;
  province_slug: string;
  price: string | number;
  source: string;
  captured_at: string | Date;
  ingested_at: string | Date;
  reference_source: string | null;
  reason: string | null;
  recorded_by: string | null;
  request_hash: string | null;
}

export class MysqlPriceHistoryRepository implements PriceHistoryRepository {
  constructor(private readonly pool: MysqlPoolLike) {}

  async append(row: NewPriceRow): Promise<void> {
    const q = row.quote;
    try {
      await this.pool.query(
        `INSERT INTO price_history
           (id, commodity, item_name, item_slug, province, province_slug, price, source,
            captured_at, ingested_at, reference_source, reason, recorded_by, idempotency_key, request_hash)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          q.id,
          q.commodity,
          q.itemName,
          q.itemSlug,
          q.province,
          q.provinceSlug,
          q.price,
          q.source,
          q.capturedAt,
          toMysqlDateTime(q.ingestedAt),
          q.referenceSource,
          q.reason,
          q.recordedBy,
          row.idempotencyKey,
          row.requestHash,
        ],
      );
    } catch (err) {
      const e = err as { code?: unknown; errno?: unknown };
      if (e && (e.code === 'ER_DUP_ENTRY' || e.errno === 1062)) throw new DuplicateIdempotencyKeyError();
      throw err;
    }
  }

  async findByIdempotencyKey(recordedBy: string, idempotencyKey: string): Promise<StoredManualPrice | undefined> {
    const [rows] = await this.pool.query(
      `SELECT id, commodity, item_name, item_slug, province, province_slug, price, source,
              captured_at, ingested_at, reference_source, reason, recorded_by, request_hash
         FROM price_history
        WHERE recorded_by = ? AND idempotency_key = ?
        LIMIT 1`,
      [recordedBy, idempotencyKey],
    );
    const r = (rows as PriceRow[])[0];
    if (!r) return undefined;
    return { quote: fromRow(r), requestHash: r.request_hash ?? '' };
  }
}

function fromRow(r: PriceRow): PriceQuote {
  return {
    id: r.id,
    commodity: 'COFFEE',
    itemName: r.item_name,
    itemSlug: r.item_slug,
    province: r.province,
    provinceSlug: r.province_slug,
    price: typeof r.price === 'number' ? r.price.toFixed(2) : r.price,
    source: r.source as PriceQuote['source'],
    capturedAt: toDateOnly(r.captured_at),
    ingestedAt: toIsoUtc(r.ingested_at),
    referenceSource: r.reference_source,
    reason: r.reason,
    recordedBy: r.recorded_by,
  };
}

/** "2026-10-12T05:31:02.123Z" -> "2026-10-12 05:31:02.123" (stored as UTC). */
export function toMysqlDateTime(iso: string): string {
  return iso.replace('T', ' ').replace('Z', '');
}

function toDateOnly(value: string | Date): string {
  if (value instanceof Date) {
    // mysql2 builds DATE values at local midnight; read back the local calendar date.
    const y = value.getFullYear();
    const m = String(value.getMonth() + 1).padStart(2, '0');
    const d = String(value.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  return value.slice(0, 10);
}

function toIsoUtc(value: string | Date): string {
  if (value instanceof Date) return value.toISOString();
  return `${value.replace(' ', 'T')}${value.endsWith('Z') ? '' : 'Z'}`;
}
