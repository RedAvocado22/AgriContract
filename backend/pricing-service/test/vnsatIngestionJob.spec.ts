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
});
