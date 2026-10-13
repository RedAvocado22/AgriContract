import type { AddressInfo } from 'node:net';
import type { Server } from 'node:http';
import type { Logger, PriceCache } from '../src/application/port.js';
import {
  DuplicateIdempotencyKeyError,
  type NewPriceRow,
  type PriceHistoryRepository,
  type StoredManualPrice,
} from '../src/domain/priceHistoryRepository.js';
import type { PriceQuote } from '../src/domain/priceQuote.js';
import { createApp, type AppDeps } from '../src/infrastructure/http/app.js';

export const GATEWAY_SECRET = 'test-gateway-secret';

/** In-memory append-only price_history. */
export class InMemoryPriceHistory implements PriceHistoryRepository {
  rows: NewPriceRow[] = [];
  events: string[] = [];
  /** Delay inside append, to let concurrent requests race on the same key. */
  appendDelayMs = 0;

  async append(row: NewPriceRow): Promise<void> {
    if (this.appendDelayMs) await new Promise((r) => setTimeout(r, this.appendDelayMs));
    const dup = this.rows.some(
      (r) => r.quote.recordedBy === row.quote.recordedBy && r.idempotencyKey === row.idempotencyKey,
    );
    if (dup) throw new DuplicateIdempotencyKeyError();
    this.rows.push(structuredClone(row));
    this.events.push(`append:${row.quote.id}`);
  }

  async findByIdempotencyKey(recordedBy: string, key: string): Promise<StoredManualPrice | undefined> {
    const r = this.rows.find((x) => x.quote.recordedBy === recordedBy && x.idempotencyKey === key);
    return r ? { quote: structuredClone(r.quote), requestHash: r.requestHash } : undefined;
  }
}

export class FakeCache implements PriceCache {
  stored: PriceQuote[] = [];
  fail = false;
  constructor(private readonly events?: string[]) {}

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

export interface TestApp {
  baseUrl: string;
  repo: InMemoryPriceHistory;
  cache: FakeCache;
  logs: LogLine[];
  close(): Promise<void>;
}

export async function startApp(overrides: Partial<AppDeps> = {}): Promise<TestApp> {
  const repo = new InMemoryPriceHistory();
  const cache = new FakeCache(repo.events);
  const logs: LogLine[] = [];
  let seq = 0;
  const server: Server = createApp({
    repository: repo,
    cache,
    logger: memoryLogger(logs),
    gatewaySecret: GATEWAY_SECRET,
    now: () => new Date('2026-10-12T05:00:00.000Z'), // 12:00 ICT
    newId: () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`,
    ...overrides,
  });
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  const { port } = server.address() as AddressInfo;
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    repo,
    cache,
    logs,
    close: () => new Promise<void>((r) => server.close(() => r())),
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

export function adminHeaders(overrides: Record<string, string> = {}): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-Gateway-Secret': GATEWAY_SECRET,
    'X-User-Id': 'admin-1',
    'X-User-Role': 'ADMIN',
    'Idempotency-Key': 'key-1',
    ...overrides,
  };
}

export async function postManual(
  app: TestApp,
  body: unknown,
  headers: Record<string, string | undefined> = adminHeaders(),
): Promise<{ status: number; json: any; headers: Headers }> {
  const clean = Object.fromEntries(Object.entries(headers).filter(([, v]) => v !== undefined)) as Record<string, string>;
  const res = await fetch(`${app.baseUrl}/api/v1/prices/manual`, {
    method: 'POST',
    headers: clean,
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
  return { status: res.status, json: await res.json(), headers: res.headers };
}
