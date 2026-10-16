import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { businessToday, requestHash, validateManualPrice } from '../src/domain/manualPrice.js';
import { DuplicateIdempotencyKeyError } from '../src/domain/priceHistoryRepository.js';
import { slugify, type PriceQuote } from '../src/domain/priceQuote.js';
import { RedisPriceCache, cacheKey, type RedisClientLike } from '../src/infrastructure/cache/redisPriceCache.js';
import { loadConfig } from '../src/infrastructure/config.js';
import { migrationFiles } from '../src/infrastructure/persistence/migrate.js';
import {
  MysqlPriceHistoryRepository,
  toMysqlDateTime,
  type MysqlPoolLike,
} from '../src/infrastructure/persistence/mysqlPriceHistoryRepository.js';
import { validBody } from './fakes.js';

const here = dirname(fileURLToPath(import.meta.url));

describe('slugify / cache key (design §2)', () => {
  it('matches the design examples', () => {
    assert.equal(slugify('Cà phê Robusta nhân xô'), 'ca-phe-robusta-nhan-xo');
    assert.equal(slugify('Đắk Lắk'), 'dak-lak');
    assert.equal(slugify('  Lâm   Đồng '), 'lam-dong');
    assert.equal(cacheKey('dak-lak', 'ca-phe-robusta-nhan-xo'), 'price:coffee:dak-lak:ca-phe-robusta-nhan-xo');
  });
});

describe('validateManualPrice', () => {
  it('accepts a valid body', () => {
    const r = validateManualPrice(validBody(), '2026-10-12');
    assert.equal(r.ok, true);
  });

  it('rejects non-object bodies', () => {
    for (const b of [null, [], 'x', 1]) assert.equal(validateManualPrice(b, '2026-10-12').ok, false);
  });

  it('enforces length limits', () => {
    assert.equal(validateManualPrice(validBody({ reason: 'x'.repeat(501) }), '2026-10-12').ok, false);
    assert.equal(validateManualPrice(validBody({ reason: 'x'.repeat(500) }), '2026-10-12').ok, true);
  });

  it('request hash ignores surrounding whitespace but not values', () => {
    const a = validateManualPrice(validBody(), '2026-10-12');
    const b = validateManualPrice(validBody({ province: ' Đắk Lắk ' }), '2026-10-12');
    const c = validateManualPrice(validBody({ price: '1.00' }), '2026-10-12');
    assert.ok(a.ok && b.ok && c.ok);
    assert.equal(requestHash(a.value), requestHash(b.value));
    assert.notEqual(requestHash(a.value), requestHash(c.value));
  });
});

describe('businessToday (Asia/Ho_Chi_Minh)', () => {
  it('rolls over at midnight ICT, not UTC', () => {
    assert.equal(businessToday(new Date('2026-10-12T16:59:59Z')), '2026-10-12');
    assert.equal(businessToday(new Date('2026-10-12T17:00:00Z')), '2026-10-13');
  });
});

describe('RedisPriceCache', () => {
  const quote = (capturedAt: string, ingestedAt: string, price: string): PriceQuote => ({
    id: price,
    commodity: 'COFFEE',
    itemName: 'Cà phê Robusta nhân xô',
    itemSlug: 'ca-phe-robusta-nhan-xo',
    province: 'Đắk Lắk',
    provinceSlug: 'dak-lak',
    price,
    source: 'OPERATOR_MANUAL',
    capturedAt,
    ingestedAt,
    referenceSource: 'x',
    reason: 'y',
    recordedBy: 'a',
  });

  function fakeRedis(): RedisClientLike & { data: Map<string, string> } {
    const data = new Map<string, string>();
    return {
      data,
      get: async (k) => data.get(k) ?? null,
      set: async (k, v) => void data.set(k, v),
    };
  }

  it('writes the quote under price:coffee:{province}:{item} without TTL', async () => {
    const redis = fakeRedis();
    await new RedisPriceCache(redis, 1000).putLatest(quote('2026-10-10', '2026-10-12T05:00:00.000Z', '1.00'));
    assert.deepEqual([...redis.data.keys()], ['price:coffee:dak-lak:ca-phe-robusta-nhan-xo']);
  });

  it('a back-dated correction does not replace a price for a later date', async () => {
    const redis = fakeRedis();
    const cache = new RedisPriceCache(redis, 1000);
    await cache.putLatest(quote('2026-10-10', '2026-10-12T05:00:00.000Z', 'newer'));
    await cache.putLatest(quote('2026-10-01', '2026-10-12T06:00:00.000Z', 'older'));
    assert.equal(JSON.parse(redis.data.values().next().value!).price, 'newer');
  });

  it('a same-day correction entered later replaces the cached one', async () => {
    const redis = fakeRedis();
    const cache = new RedisPriceCache(redis, 1000);
    await cache.putLatest(quote('2026-10-10', '2026-10-12T05:00:00.000Z', 'first'));
    await cache.putLatest(quote('2026-10-10', '2026-10-12T06:00:00.000Z', 'fixed'));
    assert.equal(JSON.parse(redis.data.values().next().value!).price, 'fixed');
  });
});

describe('MysqlPriceHistoryRepository', () => {
  class RecordingPool implements MysqlPoolLike {
    calls: Array<{ sql: string; values?: unknown[] }> = [];
    rows: unknown = [];
    error: unknown;
    async query(sql: string, values?: unknown[]): Promise<[unknown, unknown]> {
      this.calls.push({ sql, values });
      if (this.error) throw this.error;
      return [this.rows, []];
    }
  }
  const q: PriceQuote = {
    id: 'id-1',
    commodity: 'COFFEE',
    itemName: 'Cà phê Robusta nhân xô',
    itemSlug: 'ca-phe-robusta-nhan-xo',
    province: 'Đắk Lắk',
    provinceSlug: 'dak-lak',
    price: '120500.00',
    source: 'OPERATOR_MANUAL',
    capturedAt: '2026-10-10',
    ingestedAt: '2026-10-12T05:00:00.123Z',
    referenceSource: 'ref',
    reason: 'why',
    recordedBy: 'op-1',
  };

  it('INSERTs every attribution column, ingested_at as UTC DATETIME(3)', async () => {
    const pool = new RecordingPool();
    await new MysqlPriceHistoryRepository(pool).append({ quote: q, idempotencyKey: 'k', requestHash: 'h' });
    const call = pool.calls[0]!;
    assert.match(call.sql, /^INSERT INTO price_history/);
    assert.deepEqual(call.values, [
      'id-1', 'COFFEE', 'Cà phê Robusta nhân xô', 'ca-phe-robusta-nhan-xo', 'Đắk Lắk', 'dak-lak',
      '120500.00', 'OPERATOR_MANUAL', '2026-10-10', '2026-10-12 05:00:00.123', 'ref', 'why', 'op-1', 'k', 'h',
    ]);
    assert.equal(toMysqlDateTime('2026-10-12T05:00:00.123Z'), '2026-10-12 05:00:00.123');
  });

  it('maps a duplicate key error to DuplicateIdempotencyKeyError, rethrows others', async () => {
    const pool = new RecordingPool();
    const repo = new MysqlPriceHistoryRepository(pool);
    pool.error = Object.assign(new Error('dup'), { code: 'ER_DUP_ENTRY', errno: 1062 });
    await assert.rejects(repo.append({ quote: q, idempotencyKey: 'k', requestHash: 'h' }), DuplicateIdempotencyKeyError);
    pool.error = Object.assign(new Error('timeout'), { code: 'ER_LOCK_WAIT_TIMEOUT' });
    await assert.rejects(repo.append({ quote: q, idempotencyKey: 'k', requestHash: 'h' }), /timeout/);
  });

  it('reads a stored row back into the same quote (dateStrings mode)', async () => {
    const pool = new RecordingPool();
    pool.rows = [{
      id: 'id-1', commodity: 'COFFEE', item_name: q.itemName, item_slug: q.itemSlug, province: q.province,
      province_slug: q.provinceSlug, price: '120500.00', source: 'OPERATOR_MANUAL', captured_at: '2026-10-10',
      ingested_at: '2026-10-12 05:00:00.123', reference_source: 'ref', reason: 'why', recorded_by: 'op-1',
      request_hash: 'h',
    }];
    const found = await new MysqlPriceHistoryRepository(pool).findByIdempotencyKey('op-1', 'k');
    assert.deepEqual(found, { quote: q, requestHash: 'h' });
    assert.deepEqual(pool.calls[0]!.values, ['op-1', 'k']);
  });

  it('is append-only: the repository source contains no UPDATE or DELETE', () => {
    const src = readFileSync(resolve(here, '../src/infrastructure/persistence/mysqlPriceHistoryRepository.ts'), 'utf8');
    assert.doesNotMatch(src, /\b(UPDATE|DELETE)\s/);
  });
});

describe('migration (additive)', () => {
  const sql = readFileSync(resolve(here, '../migrations/V1__create_price_history.sql'), 'utf8');

  it('only creates new tables', () => {
    const statements = sql.replace(/--.*$/gm, '');
    assert.match(statements, /CREATE TABLE IF NOT EXISTS price_history/);
    assert.doesNotMatch(statements, /\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE)\b/i);
  });

  it('stores source, applicable date and ingestion timestamp, restricted to COFFEE', () => {
    for (const col of ['source', 'captured_at', 'ingested_at', 'reference_source', 'reason', 'recorded_by']) {
      assert.match(sql, new RegExp(`\\b${col}\\b`));
    }
    assert.match(sql, /CHECK \(commodity = 'COFFEE'\)/);
    assert.match(sql, /'VNSAT_SCRAPE', 'OPERATOR_MANUAL'/);
  });

  it('runner picks migration files in version order', () => {
    assert.deepEqual(migrationFiles(resolve(here, '../migrations')), ['V1__create_price_history.sql']);
  });
});

describe('config', () => {
  it('requires the gateway secret and defaults to port 8091 / pricing_db', () => {
    assert.throws(() => loadConfig({}), /GATEWAY_INTERNAL_SECRET/);
    const c = loadConfig({ GATEWAY_INTERNAL_SECRET: 's' });
    assert.equal(c.port, 8091);
    assert.equal(c.db.database, 'pricing_db');
    assert.equal(c.redis.url, undefined);
  });

  it('Redis timeouts default to 2000 ms connect / 500 ms per update and must be positive integers', () => {
    const c = loadConfig({ GATEWAY_INTERNAL_SECRET: 's' });
    assert.equal(c.redis.connectTimeoutMs, 2000);
    assert.equal(c.redis.commandTimeoutMs, 500);
    assert.equal(loadConfig({ GATEWAY_INTERNAL_SECRET: 's', REDIS_COMMAND_TIMEOUT_MS: '250' }).redis.commandTimeoutMs, 250);
    assert.throws(() => loadConfig({ GATEWAY_INTERNAL_SECRET: 's', REDIS_COMMAND_TIMEOUT_MS: '0' }), /REDIS_COMMAND_TIMEOUT_MS/);
    assert.throws(() => loadConfig({ GATEWAY_INTERNAL_SECRET: 's', REDIS_CONNECT_TIMEOUT_MS: 'abc' }), /REDIS_CONNECT_TIMEOUT_MS/);
  });
});
