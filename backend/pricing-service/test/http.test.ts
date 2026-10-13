import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { adminHeaders, postManual, startApp, validBody, type TestApp } from './fakes.js';

describe('POST /api/v1/prices/manual', () => {
  let app: TestApp;
  beforeEach(async () => {
    app = await startApp();
  });
  afterEach(async () => {
    await app.close();
  });

  describe('admin authorization', () => {
    it('ADMIN can record a manual price (201)', async () => {
      const res = await postManual(app, validBody());
      assert.equal(res.status, 201);
      assert.equal(app.repo.rows.length, 1);
    });

    it('OPERATOR can record a manual price (data-governance §5.4)', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Role': 'OPERATOR', 'X-User-Id': 'op-1' }));
      assert.equal(res.status, 201);
      assert.equal(res.json.recordedBy, 'op-1');
    });

    it('accepts ADMIN among several comma-separated roles, case-insensitive', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Role': 'offline_access, admin' }));
      assert.equal(res.status, 201);
    });

    for (const role of ['SELLER', 'BUYER', 'INSPECTOR', 'SELLER,BUYER']) {
      it(`rejects ${role} with 403 and stores nothing`, async () => {
        const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Role': role }));
        assert.equal(res.status, 403);
        assert.equal(res.json.code, 'PRICE_MANUAL_FORBIDDEN');
        assert.equal(app.repo.rows.length, 0);
      });
    }

    it('rejects a request that did not come through the gateway (no secret)', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-Gateway-Secret': undefined as unknown as string }));
      assert.equal(res.status, 403);
      assert.equal(res.json.code, 'GATEWAY_IDENTITY_REQUIRED');
      assert.equal(app.repo.rows.length, 0);
    });

    it('rejects a forged role header with a wrong gateway secret', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-Gateway-Secret': 'guess' }));
      assert.equal(res.status, 403);
      assert.equal(app.repo.rows.length, 0);
    });

    it('rejects a gateway request without user identity', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Id': undefined as unknown as string }));
      assert.equal(res.status, 403);
      assert.equal(app.repo.rows.length, 0);
    });

    it('checks authorization before reading/validating the body', async () => {
      const res = await postManual(app, '{broken', adminHeaders({ 'X-User-Role': 'SELLER' }));
      assert.equal(res.status, 403);
    });
  });

  describe('source and timestamp stored', () => {
    it('stores source=ADMIN_MANUAL, applicable date, ingestion time, reference source, reason and actor', async () => {
      const res = await postManual(app, validBody());
      assert.equal(res.status, 201);
      const stored = app.repo.rows[0]!.quote;
      assert.deepEqual(stored, {
        id: '00000000-0000-4000-8000-000000000001',
        commodity: 'COFFEE',
        itemName: 'Cà phê Robusta nhân xô',
        itemSlug: 'ca-phe-robusta-nhan-xo',
        province: 'Đắk Lắk',
        provinceSlug: 'dak-lak',
        price: '120500.00',
        source: 'ADMIN_MANUAL',
        capturedAt: '2026-10-10',
        ingestedAt: '2026-10-12T05:00:00.000Z',
        referenceSource: 'Bản tin giá Sở NN&MT Đắk Lắk 10/10/2026',
        reason: 'VNSAT không cập nhật quá 14 ngày',
        recordedBy: 'admin-1',
      });
      assert.deepEqual(res.json, stored);
    });

    it('trims text fields', async () => {
      const res = await postManual(app, validBody({ province: '  Gia Lai  ', reason: '  nguồn lỗi ' }));
      assert.equal(res.json.province, 'Gia Lai');
      assert.equal(res.json.provinceSlug, 'gia-lai');
      assert.equal(res.json.reason, 'nguồn lỗi');
    });

    it('is append-only: a correction is a new row and the old price is untouched', async () => {
      await postManual(app, validBody({ price: '120000.00' }), adminHeaders({ 'Idempotency-Key': 'a' }));
      await postManual(app, validBody({ price: '121000.00' }), adminHeaders({ 'Idempotency-Key': 'b' }));
      assert.deepEqual(
        app.repo.rows.map((r) => r.quote.price),
        ['120000.00', '121000.00'],
      );
    });

    it('updates the cache only after the row is written', async () => {
      const res = await postManual(app, validBody());
      assert.deepEqual(app.repo.events, [`append:${res.json.id}`, `cache:${res.json.id}`]);
      assert.equal(app.cache.stored.length, 1);
    });

    it('a Redis failure does not fail the request (MySQL is the source of truth)', async () => {
      app.cache.fail = true;
      const res = await postManual(app, validBody());
      assert.equal(res.status, 201);
      assert.equal(app.repo.rows.length, 1);
      assert.ok(app.logs.some((l) => l.level === 'warn' && /Redis update failed/.test(l.message)));
    });
  });

  describe('validation', () => {
    const cases: Array<[string, Record<string, unknown>]> = [
      ['non-coffee commodity', { commodity: 'RICE' }],
      ['missing province', { province: '' }],
      ['missing reason', { reason: '   ' }],
      ['missing reference source', { referenceSource: undefined }],
      ['numeric price', { price: 120500 }],
      ['price without decimals', { price: '120500' }],
      ['zero price', { price: '0.00' }],
      ['negative price', { price: '-1.00' }],
      ['price above DECIMAL(10,2)', { price: '100000000.00' }],
      ['price with leading zeros', { price: '00100.00' }],
      ['price with a leading zero', { price: '0100.00' }],
      ['price with one decimal', { price: '100.0' }],
      ['impossible date', { capturedAt: '2026-02-30' }],
      ['date-time instead of date', { capturedAt: '2026-10-10T00:00:00Z' }],
      ['future date', { capturedAt: '2026-10-13' }],
      ['unknown field', { quoteId: 'x' }],
      ['itemName with no letters', { itemName: '---' }],
    ];
    for (const [name, patch] of cases) {
      it(`rejects ${name} with 400 and stores nothing`, async () => {
        const body = validBody(patch);
        for (const [k, v] of Object.entries(body)) if (v === undefined) delete body[k];
        const res = await postManual(app, body);
        assert.equal(res.status, 400, JSON.stringify(res.json));
        assert.equal(res.json.code, 'VALIDATION_FAILED');
        assert.equal(app.repo.rows.length, 0);
      });
    }

    it('accepts a price below 1 VND written canonically ("0.50")', async () => {
      const res = await postManual(app, validBody({ price: '0.50' }));
      assert.equal(res.status, 201);
      assert.equal(res.json.price, '0.50');
    });

    it('accepts today (business date in Vietnam)', async () => {
      const res = await postManual(app, validBody({ capturedAt: '2026-10-12' }));
      assert.equal(res.status, 201);
    });

    it('rejects malformed JSON', async () => {
      const res = await postManual(app, '{broken');
      assert.equal(res.status, 400);
    });

    it('rejects a non-JSON content type with 415', async () => {
      const res = await postManual(app, 'a=b', adminHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }));
      assert.equal(res.status, 415);
    });
  });

  describe('Idempotency-Key', () => {
    it('is required (400)', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'Idempotency-Key': undefined as unknown as string }));
      assert.equal(res.status, 400);
      assert.equal(res.json.code, 'IDEMPOTENCY_KEY_REQUIRED');
      assert.equal(app.repo.rows.length, 0);
    });

    it('a retry with the same key and body returns the original 201 response, no new row', async () => {
      const first = await postManual(app, validBody());
      const second = await postManual(app, validBody());
      assert.equal(second.status, 201);
      assert.deepEqual(second.json, first.json);
      assert.equal(second.headers.get('idempotent-replayed'), 'true');
      assert.equal(app.repo.rows.length, 1);
      // The replay refreshes the cache again (a failed first refresh would otherwise stay stale).
      assert.deepEqual(app.cache.stored.map((q) => q.id), [first.json.id, first.json.id]);
    });

    it('reusing a key with a different body is a 409 state conflict', async () => {
      await postManual(app, validBody());
      const res = await postManual(app, validBody({ price: '99000.00' }));
      assert.equal(res.status, 409);
      assert.equal(res.json.code, 'IDEMPOTENCY_KEY_STATE_CONFLICT');
      assert.equal(app.repo.rows.length, 1);
    });

    it('keys are scoped per actor', async () => {
      await postManual(app, validBody());
      const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Id': 'admin-2' }));
      assert.equal(res.status, 201);
      assert.equal(app.repo.rows.length, 2);
    });

    it('concurrent requests with the same key create exactly one row', async () => {
      app.repo.appendDelayMs = 20;
      const [a, b] = await Promise.all([postManual(app, validBody()), postManual(app, validBody())]);
      assert.equal(a.status, 201);
      assert.equal(b.status, 201);
      assert.equal(a.json.id, b.json.id);
      assert.equal(app.repo.rows.length, 1);
    });
  });

  describe('routing', () => {
    it('GET on the manual path is 405', async () => {
      const res = await fetch(`${app.baseUrl}/api/v1/prices/manual`);
      assert.equal(res.status, 405);
    });

    it('unknown path is 404 with the shared error envelope', async () => {
      const res = await fetch(`${app.baseUrl}/api/v1/nope`);
      const json = await res.json();
      assert.equal(res.status, 404);
      assert.deepEqual(Object.keys(json).sort(), ['code', 'error', 'message', 'path', 'status', 'timestamp', 'traceId']);
      assert.equal(json.path, '/api/v1/nope');
    });

    it('health endpoint', async () => {
      const res = await fetch(`${app.baseUrl}/health`);
      assert.equal(res.status, 200);
      assert.deepEqual(await res.json(), { status: 'UP' });
    });

    it('propagates X-Request-Id as traceId', async () => {
      const res = await postManual(app, validBody(), adminHeaders({ 'X-User-Role': 'SELLER', 'X-Request-Id': 'trace-123' }));
      assert.equal(res.json.traceId, 'trace-123');
      assert.equal(res.headers.get('x-trace-id'), 'trace-123');
    });

    it('unexpected repository errors become 500 without leaking details', async () => {
      await app.close();
      app = await startApp({
        repository: {
          append: async () => {
            throw new Error('Access denied for user root@10.0.0.5');
          },
          findByIdempotencyKey: async () => undefined,
        },
      });
      const res = await postManual(app, validBody());
      assert.equal(res.status, 500);
      assert.equal(res.json.code, 'INTERNAL_ERROR');
      assert.doesNotMatch(res.json.message, /root@/);
    });
  });
});
