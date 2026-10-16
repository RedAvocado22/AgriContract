/**
 * Review #157:
 *  - an idempotent replay returns exactly the original successful response, even though the replay
 *    is read back from MySQL (error-catalog.md);
 *  - a replay refreshes Redis, so a cache update that failed on the first attempt does not leave an
 *    older quote cached forever (design §2/§4: Redis is a self-healing cache of MySQL).
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { after, afterEach, before, describe, it } from 'node:test';
import mysql from 'mysql2/promise';
import type { PriceQuote } from '../src/domain/priceQuote.js';
import { RedisPriceCache, cacheKey, type RedisClientLike } from '../src/infrastructure/cache/redisPriceCache.js';
import { applyMigrations } from '../src/infrastructure/persistence/migrate.js';
import {
  MysqlPriceHistoryRepository,
  createMysqlPool,
  type MysqlPoolLike,
} from '../src/infrastructure/persistence/mysqlPriceHistoryRepository.js';
import { operatorHeaders, postManual, startApp, validBody, type TestApp } from './fakes.js';

/**
 * Stand-in for MySQL that converts values the way price_history's column types do and hands them
 * back the way mysql2 does with dateStrings: true. Unlike the in-memory fake, it does NOT keep the
 * strings that were sent, so it exposes any difference between the first response and a replay.
 */
class MysqlTypedPool implements MysqlPoolLike {
  private readonly rows: Array<Record<string, unknown>> = [];

  async query(sql: string, values: unknown[] = []): Promise<[unknown, unknown]> {
    if (/^\s*INSERT INTO price_history/.test(sql)) {
      const columns = /\(([^)]+)\)\s*VALUES/.exec(sql)![1]!.split(',').map((c) => c.trim());
      const row = Object.fromEntries(columns.map((c, i) => [c, values[i]]));
      row.price = decimal10_2(String(row.price)); // DECIMAL(10,2)
      row.captured_at = String(row.captured_at).slice(0, 10); // DATE
      row.ingested_at = String(row.ingested_at).slice(0, 23); // DATETIME(3)
      const dup = this.rows.some((r) => r.recorded_by === row.recorded_by && r.idempotency_key === row.idempotency_key);
      if (dup) throw Object.assign(new Error('Duplicate entry'), { code: 'ER_DUP_ENTRY', errno: 1062 });
      this.rows.push(row);
      return [{ affectedRows: 1 }, []];
    }
    if (/^\s*SELECT/.test(sql)) {
      const [recordedBy, key] = values;
      return [this.rows.filter((r) => r.recorded_by === recordedBy && r.idempotency_key === key).slice(0, 1), []];
    }
    throw new Error(`unexpected SQL: ${sql}`);
  }
}

/** MySQL DECIMAL(10,2) keeps the number, not the text: "00100.00" is stored and returned as "100.00". */
function decimal10_2(text: string): string {
  const [int = '0', frac = ''] = text.split('.');
  return `${BigInt(int)}.${frac.padEnd(2, '0').slice(0, 2)}`;
}

const CANONICAL_PRICES = ['120500.00', '0.50', '1.00', '99999999.99'];

describe('idempotent replay returns the original response after the MySQL round trip', () => {
  let app: TestApp;
  afterEach(async () => {
    await app.close();
  });

  for (const price of CANONICAL_PRICES) {
    it(`price "${price}": replay body is identical to the first 201`, async () => {
      app = await startApp({ repository: new MysqlPriceHistoryRepository(new MysqlTypedPool()) });
      const first = await postManual(app, validBody({ price }));
      const replay = await postManual(app, validBody({ price }));
      assert.equal(first.status, 201);
      assert.equal(replay.status, 201);
      assert.equal(replay.headers.get('idempotent-replayed'), 'true');
      assert.deepEqual(replay.json, first.json);
    });
  }

  it('"00100.00" is rejected up front, because MySQL would hand it back as "100.00"', async () => {
    assert.equal(decimal10_2('00100.00'), '100.00');
    app = await startApp({ repository: new MysqlPriceHistoryRepository(new MysqlTypedPool()) });
    const res = await postManual(app, validBody({ price: '00100.00' }));
    assert.equal(res.status, 400);
    assert.equal(res.json.code, 'VALIDATION_FAILED');
  });
});

describe('a replay heals a cache update that failed on the first attempt', () => {
  /** node-redis stand-in whose SET can be switched off to simulate a Redis outage. */
  function flakyRedis(): RedisClientLike & { data: Map<string, string>; setFails: boolean } {
    const data = new Map<string, string>();
    const client = {
      data,
      setFails: false,
      get: async (k: string) => data.get(k) ?? null,
      set: async (k: string, v: string) => {
        if (client.setFails) throw new Error('redis down');
        data.set(k, v);
      },
    };
    return client;
  }

  const key = cacheKey('dak-lak', 'ca-phe-robusta-nhan-xo');
  const cached = (redis: { data: Map<string, string> }) => JSON.parse(redis.data.get(key)!) as PriceQuote;

  let app: TestApp;
  afterEach(async () => {
    await app.close();
  });

  it('Redis holds A; B is stored but the cache write fails; retrying B puts B in Redis', async () => {
    const redis = flakyRedis();
    app = await startApp({ cache: new RedisPriceCache(redis, 1000) });

    const a = await postManual(app, validBody({ price: '118000.00', capturedAt: '2026-10-01' }), operatorHeaders({ 'Idempotency-Key': 'a' }));
    assert.equal(cached(redis).id, a.json.id);

    redis.setFails = true;
    const b = await postManual(app, validBody({ price: '121000.00', capturedAt: '2026-10-10' }), operatorHeaders({ 'Idempotency-Key': 'b' }));
    assert.equal(b.status, 201, 'a cache failure must not fail the request');
    assert.equal(cached(redis).id, a.json.id, 'Redis still holds the older quote A');

    redis.setFails = false;
    const retry = await postManual(app, validBody({ price: '121000.00', capturedAt: '2026-10-10' }), operatorHeaders({ 'Idempotency-Key': 'b' }));
    assert.equal(retry.status, 201);
    assert.equal(retry.headers.get('idempotent-replayed'), 'true');
    assert.deepEqual(retry.json, b.json);
    assert.equal(cached(redis).id, b.json.id, 'the replay refreshed Redis with B');
    assert.equal(app.repo.rows.length, 2, 'the replay did not insert another row');
  });

  it('a replay never replaces a newer cached price', async () => {
    const redis = flakyRedis();
    app = await startApp({ cache: new RedisPriceCache(redis, 1000) });
    const older = validBody({ price: '118000.00', capturedAt: '2026-10-01' });
    await postManual(app, older, operatorHeaders({ 'Idempotency-Key': 'old' }));
    const newer = await postManual(app, validBody({ price: '121000.00', capturedAt: '2026-10-10' }), operatorHeaders({ 'Idempotency-Key': 'new' }));
    await postManual(app, older, operatorHeaders({ 'Idempotency-Key': 'old' }));
    assert.equal(cached(redis).id, newer.json.id);
  });
});

/**
 * Same replay check against a real MySQL, with the production pool options.
 * Runs only when PRICING_TEST_MYSQL_URL is set, e.g. with the docker-compose MySQL:
 *   PRICING_TEST_MYSQL_URL=mysql://root:root@localhost:3306/pricing_test npm test
 * (the database must exist; the test applies the migrations itself).
 */
const MYSQL_URL = process.env.PRICING_TEST_MYSQL_URL;
describe('real MySQL (PRICING_TEST_MYSQL_URL)', { skip: MYSQL_URL ? false : 'PRICING_TEST_MYSQL_URL not set' }, () => {
  let pool: mysql.Pool;
  let app: TestApp;

  before(async () => {
    const conn = await mysql.createConnection({ uri: MYSQL_URL!, multipleStatements: true });
    try {
      await applyMigrations(conn);
    } finally {
      await conn.end();
    }
    pool = createMysqlPool({ uri: MYSQL_URL! });
    app = await startApp({
      repository: new MysqlPriceHistoryRepository(pool),
      newId: () => randomUUID(),
    });
  });

  after(async () => {
    await app?.close();
    await pool?.end();
  });

  it('DECIMAL(10,2) returns "00100.00" as "100.00" (why non-canonical prices are rejected)', async () => {
    const [rows] = await pool.query("SELECT CAST('00100.00' AS DECIMAL(10,2)) AS v");
    assert.equal((rows as Array<{ v: string }>)[0]!.v, '100.00');
  });

  for (const price of CANONICAL_PRICES) {
    it(`price "${price}": replay read from MySQL equals the first 201`, async () => {
      const headers = operatorHeaders({ 'Idempotency-Key': `it-${randomUUID()}` });
      const first = await postManual(app, validBody({ price }), headers);
      const replay = await postManual(app, validBody({ price }), headers);
      assert.equal(first.status, 201, JSON.stringify(first.json));
      assert.equal(replay.status, 201);
      assert.equal(replay.headers.get('idempotent-replayed'), 'true');
      assert.deepEqual(replay.json, first.json);
    });
  }
});
