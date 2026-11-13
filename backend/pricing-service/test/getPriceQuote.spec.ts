import assert from 'node:assert/strict';
import { PriceNotFound } from '../src/application/errors';
import { getPriceQuote, type PriceReadDeps } from '../src/application/usecase/getPriceQuote';
import { listPriceItems } from '../src/application/usecase/listPriceItems';
import type { PriceQuote } from '../src/domain/priceQuote';
import { FakeCache, InMemoryPriceHistory, memoryLogger, type LogLine } from './fakes';

// 2026-10-12 12:00 ICT.
const NOW = new Date('2026-10-12T05:00:00.000Z');

function quote(overrides: Partial<PriceQuote> = {}): PriceQuote {
  return {
    id: 'q-1', commodity: 'COFFEE', itemName: 'Cà phê Robusta nhân xô', itemSlug: 'ca-phe-robusta-nhan-xo',
    province: 'Đắk Lắk', provinceSlug: 'dak-lak', price: '120500.00', source: 'VNSAT_SCRAPE',
    capturedAt: '2026-10-10', ingestedAt: '2026-10-11T00:00:00.000Z', referenceSource: 'https://vnsat',
    reason: null, recordedBy: null, ...overrides,
  };
}

async function setup(quotes: PriceQuote[]) {
  const repo = new InMemoryPriceHistory();
  for (const q of quotes) await repo.append({ quote: q, idempotencyKey: q.id, requestHash: q.id });
  const cache = new FakeCache();
  const logs: LogLine[] = [];
  const deps: PriceReadDeps = { repository: repo, cache, logger: memoryLogger(logs), staleThresholdDays: 14, now: () => NOW };
  return { repo, cache, logs, deps };
}

describe('getPriceQuote use case', () => {
  it('infers the item when the province has exactly one', async () => {
    const { deps } = await setup([quote()]);
    const result = await getPriceQuote(deps, 'COFFEE', 'dak-lak');
    assert.equal(result.itemSlug, 'ca-phe-robusta-nhan-xo');
    assert.equal(result.id, 'q-1');
  });

  it('is PriceNotFound when the province has several items and none is chosen', async () => {
    const { deps } = await setup([quote(), quote({ id: 'q-2', itemName: 'Cà phê Arabica', itemSlug: 'ca-phe-arabica' })]);
    await assert.rejects(getPriceQuote(deps, 'COFFEE', 'dak-lak'), PriceNotFound);
    assert.equal((await getPriceQuote(deps, 'COFFEE', 'dak-lak', 'ca-phe-arabica')).id, 'q-2');
  });

  it('falls back to MySQL when the Redis READ fails, and repairs the cache', async () => {
    const { deps, cache, logs } = await setup([quote()]);
    cache.failReads = true;
    const result = await getPriceQuote(deps, 'COFFEE', 'dak-lak', 'ca-phe-robusta-nhan-xo');
    assert.equal(result.id, 'q-1');
    assert.deepEqual(cache.stored.map((q) => q.id), ['q-1']);
    assert.ok(logs.some((l) => l.level === 'warn' && /Redis read failed/.test(l.message)));
  });

  it('serves a cache hit without touching MySQL', async () => {
    const { deps, cache, repo } = await setup([]);
    await cache.putLatest(quote({ id: 'cached' }));
    assert.equal((await getPriceQuote(deps, 'COFFEE', 'dak-lak', 'ca-phe-robusta-nhan-xo')).id, 'cached');
    assert.equal(repo.rows.length, 0);
  });

  it('stale boundary: exactly 14 days old is fresh, 15 days old is stale (Vietnam date)', async () => {
    const fresh = await setup([quote({ capturedAt: '2026-09-28' })]);
    assert.equal((await getPriceQuote(fresh.deps, 'COFFEE', 'dak-lak')).stale, false);
    const stale = await setup([quote({ capturedAt: '2026-09-27' })]);
    assert.equal((await getPriceQuote(stale.deps, 'COFFEE', 'dak-lak')).stale, true);
  });

  it('never returns recordedBy', async () => {
    const { deps } = await setup([quote({ source: 'OPERATOR_MANUAL', recordedBy: 'op-1', reason: 'fallback' })]);
    assert.equal('recordedBy' in (await getPriceQuote(deps, 'COFFEE', 'dak-lak')), false);
  });

  for (const [name, args] of [
    ['a non-COFFEE commodity', ['RICE', 'dak-lak']],
    ['a malformed province', ['COFFEE', 'Dak_Lak']],
    ['a malformed item', ['COFFEE', 'dak-lak', 'Not A Slug']],
    ['a province with no prices', ['COFFEE', 'gia-lai']],
  ] as const) {
    it(`is PriceNotFound for ${name}`, async () => {
      const { deps } = await setup([quote()]);
      const [commodity, province, item] = args as readonly string[];
      await assert.rejects(getPriceQuote(deps, commodity!, province!, item), PriceNotFound);
    });
  }
});

describe('listPriceItems use case', () => {
  it('lists the items of a province and returns [] for anything that cannot have items', async () => {
    const { deps } = await setup([quote()]);
    assert.deepEqual(await listPriceItems(deps, 'COFFEE', 'dak-lak'), [
      { itemName: 'Cà phê Robusta nhân xô', itemSlug: 'ca-phe-robusta-nhan-xo' },
    ]);
    assert.deepEqual(await listPriceItems(deps, 'RICE', 'dak-lak'), []);
    assert.deepEqual(await listPriceItems(deps, 'COFFEE', 'Dak_Lak'), []);
  });
});
