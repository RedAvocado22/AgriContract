import type { Logger, PriceCache } from '../src/application/port';
import {
  DuplicateIdempotencyKeyError,
  type NewPriceRow,
  type PriceHistoryRepository,
  type StoredManualPrice,
} from '../src/domain/priceHistoryRepository';
import type { PriceQuote } from '../src/domain/priceQuote';

export const GATEWAY_SECRET = 'test-gateway-secret';

/** In-memory append-only price_history. */
export class InMemoryPriceHistory implements PriceHistoryRepository {
  rows: NewPriceRow[] = [];
  events: string[] = [];
  /** Delay inside append, to let concurrent requests race on the same key. */
  appendDelayMs = 0;
  failures: Array<{ commodity: string; reason: string }> = [];

  async append(row: NewPriceRow): Promise<void> {
    if (this.appendDelayMs) await new Promise((r) => setTimeout(r, this.appendDelayMs));
    const dup = this.rows.some(
      (r) => r.quote.recordedBy === row.quote.recordedBy && r.idempotencyKey === row.idempotencyKey,
    );
    if (dup || (row.sourceIdentity && this.rows.some((r) => r.sourceIdentity === row.sourceIdentity))) {
      throw new DuplicateIdempotencyKeyError();
    }
    this.rows.push(structuredClone(row));
    this.events.push(`append:${row.quote.id}`);
  }

  /** When set, appendScrapedBatch throws a DB error on this (0-based) row, after the earlier rows were written. */
  failBatchAtRow: number | undefined;

  /** All-or-nothing like the MySQL transaction: works on a copy and keeps it only if every row succeeds. */
  async appendScrapedBatch(rows: NewPriceRow[]): Promise<PriceQuote[]> {
    const staged = [...this.rows];
    const inserted: PriceQuote[] = [];
    rows.forEach((row, index) => {
      if (index === this.failBatchAtRow) throw new Error('ER_LOCK_WAIT_TIMEOUT');
      if (row.sourceIdentity && staged.some((r) => r.sourceIdentity === row.sourceIdentity)) return;
      staged.push(structuredClone(row));
      inserted.push(structuredClone(row.quote));
    });
    this.rows = staged;
    for (const q of inserted) this.events.push(`append:${q.id}`);
    return inserted;
  }

  async findByIdempotencyKey(recordedBy: string, key: string): Promise<StoredManualPrice | undefined> {
    const r = this.rows.find((x) => x.quote.recordedBy === recordedBy && x.idempotencyKey === key);
    return r ? { quote: structuredClone(r.quote), requestHash: r.requestHash } : undefined;
  }

  async findLatest(_commodity: string, provinceSlug: string, itemSlug?: string): Promise<PriceQuote | undefined> {
    const rows = this.rows.map((r) => r.quote).filter((q) => q.provinceSlug === provinceSlug && (!itemSlug || q.itemSlug === itemSlug));
    rows.sort((a, b) => b.capturedAt.localeCompare(a.capturedAt) || b.ingestedAt.localeCompare(a.ingestedAt));
    return rows[0] ? structuredClone(rows[0]) : undefined;
  }

  async listItems(_commodity: string, provinceSlug: string): Promise<Array<{ itemName: string; itemSlug: string }>> {
    return [...new Map(this.rows.filter((r) => r.quote.provinceSlug === provinceSlug)
      .map((r) => [r.quote.itemSlug, { itemName: r.quote.itemName, itemSlug: r.quote.itemSlug }])).values()];
  }

  async recordIngestionFailure(commodity: string, reason: string): Promise<void> {
    this.failures.push({ commodity, reason });
  }
}

export class FakeCache implements PriceCache {
  stored: PriceQuote[] = [];
  fail = false;
  constructor(private readonly events?: string[]) {}

  async getLatest(provinceSlug: string, itemSlug: string): Promise<PriceQuote | undefined> {
    return structuredClone(this.stored.filter((q) => q.provinceSlug === provinceSlug && q.itemSlug === itemSlug)
      .sort((a, b) => b.capturedAt.localeCompare(a.capturedAt) || b.ingestedAt.localeCompare(a.ingestedAt))[0]);
  }

  async putLatest(quote: PriceQuote): Promise<void> {
    this.events?.push(`cache:${quote.id}`);
    if (this.fail) throw new Error('redis down');
    this.stored.push(quote);
  }
}

export interface LogLine {
  level: string;
  message: string;
}

export function memoryLogger(lines: LogLine[] = []): Logger {
  return {
    info: (message) => void lines.push({ level: 'info', message }),
    warn: (message) => void lines.push({ level: 'warn', message }),
    error: (message) => void lines.push({ level: 'error', message }),
  };
}

export function validBody(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    commodity: 'COFFEE',
    itemName: 'Cà phê Robusta nhân xô',
    province: 'Đắk Lắk',
    price: '120500.00',
    capturedAt: '2026-10-10',
    referenceSource: 'Bản tin giá Sở NN&MT Đắk Lắk 10/10/2026',
    reason: 'VNSAT không cập nhật quá 14 ngày',
    ...overrides,
  };
}

export function operatorHeaders(overrides: Record<string, string> = {}): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-Gateway-Secret': GATEWAY_SECRET,
    'X-User-Id': 'op-1',
    'X-User-Role': 'OPERATOR',
    'Idempotency-Key': 'key-1',
    ...overrides,
  };
}

export async function postManual(
  app: { baseUrl: string },
  body: unknown,
  headers: Record<string, string | undefined> = operatorHeaders(),
): Promise<{ status: number; json: any; headers: Headers }> {
  const clean = Object.fromEntries(Object.entries(headers).filter(([, v]) => v !== undefined)) as Record<string, string>;
  const res = await fetch(`${app.baseUrl}/api/v1/prices/manual`, {
    method: 'POST',
    headers: clean,
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
  return { status: res.status, json: await res.json(), headers: res.headers };
}
