import assert from 'node:assert/strict';
import { runVnsatPriceIngestion } from '../src/infrastructure/vnsat/vnsatPriceIngestion';
import { FakeCache, InMemoryPriceHistory, memoryLogger } from './fakes';
import type { PriceQuote } from '../src/domain/priceQuote';

const lastQuote: PriceQuote = {
  id: 'old-price', commodity: 'COFFEE', itemName: 'Cà phê Robusta nhân xô', itemSlug: 'ca-phe-robusta-nhan-xo',
  province: 'Đắk Lắk', provinceSlug: 'dak-lak', price: '87000.00', source: 'OPERATOR_MANUAL',
  capturedAt: '2026-10-10', ingestedAt: '2026-10-11T00:00:00.000Z', referenceSource: 'operator reference',
  reason: 'fallback', recordedBy: 'operator-1',
};

describe('VNSAT price ingestion', () => {
  it('keeps the last stored/cached quote and records a failure when scraping fails', async () => {
    const repo = new InMemoryPriceHistory();
    const cache = new FakeCache();
    await repo.append({ quote: lastQuote, idempotencyKey: 'manual-1', requestHash: 'hash-1' });
    await cache.putLatest(lastQuote);

    const result = await runVnsatPriceIngestion(
      { repository: repo, cache, logger: memoryLogger() },
      memoryLogger(),
      async () => { throw new Error('VNSAT unavailable'); },
      new Date('2026-11-12T00:00:00.000Z'),
    );

    assert.deepEqual(result, { rows: 0, inserted: 0 });
    assert.equal(repo.rows.length, 1);
    const preserved = await repo.findLatest('COFFEE', 'dak-lak', lastQuote.itemSlug);
    assert.equal(preserved?.id, lastQuote.id);
    assert.equal(preserved?.price, lastQuote.price);
    assert.equal(preserved?.source, lastQuote.source);
    assert.deepEqual(cache.stored, [lastQuote]);
    assert.deepEqual(repo.failures, [{ commodity: 'COFFEE', reason: 'VNSAT unavailable' }]);
  });

  it('ingests a valid row once and keeps manual entries available as the fallback path', async () => {
    const repo = new InMemoryPriceHistory();
    const cache = new FakeCache();
    const scrape = async () => [{
      itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', date: '10-11-2026', price: '88.500',
    }];
    const deps = { repository: repo, cache, logger: memoryLogger() };
    const first = await runVnsatPriceIngestion(deps, memoryLogger(), scrape, new Date('2026-11-12T00:00:00.000Z'), () => '00000000-0000-4000-8000-000000000001');
    const second = await runVnsatPriceIngestion(deps, memoryLogger(), scrape, new Date('2026-11-13T00:00:00.000Z'), () => '00000000-0000-4000-8000-000000000002');
    assert.deepEqual(first, { rows: 1, inserted: 1 });
    assert.deepEqual(second, { rows: 1, inserted: 0 });
    assert.equal(repo.rows.length, 1);
    assert.equal(repo.rows[0]?.quote.price, '88500.00');
    assert.equal(repo.rows[0]?.quote.source, 'VNSAT_SCRAPE');
    assert.equal(cache.stored.length, 1);
  });

  it('a DB error in the middle of a batch commits none of it, keeps the old quote and records a failure', async () => {
    const repo = new InMemoryPriceHistory();
    const cache = new FakeCache();
    await repo.append({ quote: lastQuote, idempotencyKey: 'manual-1', requestHash: 'hash-1' });
    await cache.putLatest(lastQuote);
    repo.failBatchAtRow = 1;
    const scrape = async () => [
      { itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', date: '10-11-2026', price: '88.500' },
      { itemName: 'Cà phê Robusta nhân xô', province: 'Gia Lai', date: '10-11-2026', price: '88.200' },
    ];

    const result = await runVnsatPriceIngestion(
      { repository: repo, cache, logger: memoryLogger() }, memoryLogger(), scrape, new Date('2026-11-12T00:00:00.000Z'),
    );

    assert.deepEqual(result, { rows: 0, inserted: 0 });
    assert.deepEqual(repo.rows.map((r) => r.quote.id), [lastQuote.id]);
    assert.deepEqual(cache.stored, [lastQuote]);
    assert.deepEqual(repo.failures, [{ commodity: 'COFFEE', reason: 'ER_LOCK_WAIT_TIMEOUT' }]);
  });

  it('repairs Redis from MySQL when a batch row was already committed by an earlier run', async () => {
    const repo = new InMemoryPriceHistory();
    const cache = new FakeCache();
    const scrape = async () => [{ itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', date: '10-11-2026', price: '88.500' }];
    const deps = { repository: repo, cache, logger: memoryLogger() };
    cache.fail = true; // first run: MySQL commits, the Redis write is lost
    await runVnsatPriceIngestion(deps, memoryLogger(), scrape, new Date('2026-11-12T00:00:00.000Z'), () => '00000000-0000-4000-8000-0000000000c1');
    cache.fail = false;

    const second = await runVnsatPriceIngestion(deps, memoryLogger(), scrape, new Date('2026-11-13T00:00:00.000Z'), () => '00000000-0000-4000-8000-0000000000c2');

    assert.deepEqual(second, { rows: 1, inserted: 0 });
    assert.deepEqual(cache.stored.map((q) => q.id), ['00000000-0000-4000-8000-0000000000c1']);
  });
});
