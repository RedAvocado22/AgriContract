import assert from 'node:assert/strict';
import type { ReferencePriceSource, ScrapedPrice } from '../src/application/port';
import { ingestVnsatPrices, type IngestVnsatDeps } from '../src/application/usecase/ingestVnsatPrices';
import type { PriceQuote } from '../src/domain/priceQuote';
import { loadConfig } from '../src/infrastructure/config';
import { VnsatPriceIngestionJob } from '../src/infrastructure/vnsat/vnsatIngestionJob';
import { FakeCache, InMemoryPriceHistory, memoryLogger } from './fakes';

const VNSAT_URL = 'https://thitruongnongsan.gov.vn/vn/nguonwmy.aspx';

const lastQuote: PriceQuote = {
  id: 'old-price', commodity: 'COFFEE', itemName: 'Cà phê Robusta nhân xô', itemSlug: 'ca-phe-robusta-nhan-xo',
  province: 'Đắk Lắk', provinceSlug: 'dak-lak', price: '87000.00', source: 'OPERATOR_MANUAL',
  capturedAt: '2026-10-10', ingestedAt: '2026-10-11T00:00:00.000Z', referenceSource: 'operator reference',
  reason: 'fallback', recordedBy: 'operator-1',
};

const robustaDakLak: ScrapedPrice = { itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', price: '88500.00', capturedAt: '2026-11-10' };
const robustaGiaLai: ScrapedPrice = { itemName: 'Cà phê Robusta nhân xô', province: 'Gia Lai', price: '88200.00', capturedAt: '2026-11-10' };
const robustaLamDong: ScrapedPrice = { itemName: 'Cà phê Robusta nhân xô', province: 'Lâm Đồng', price: '88100.00', capturedAt: '2026-11-10' };

function source(rows: ScrapedPrice[] | (() => Promise<ScrapedPrice[]>), url = VNSAT_URL): ReferencePriceSource & { calls: number } {
  const s = {
    url,
    calls: 0,
    async fetchCoffeePrices() {
      s.calls += 1;
      return typeof rows === 'function' ? rows() : rows;
    },
  };
  return s;
}

function setup(src: ReferencePriceSource) {
  const repo = new InMemoryPriceHistory();
  const cache = new FakeCache();
  let seq = 0;
  const deps: IngestVnsatDeps = {
    repository: repo,
    cache,
    logger: memoryLogger(),
    source: src,
    now: () => new Date('2026-11-12T00:00:00.000Z'),
    newId: () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`,
  };
  return { repo, cache, deps };
}

describe('ingestVnsatPrices use case', () => {
  it('keeps the last stored/cached quote and records a failure when the source fails', async () => {
    const { repo, cache, deps } = setup(source(async () => { throw new Error('VNSAT unavailable'); }));
    await repo.append({ quote: lastQuote, idempotencyKey: 'manual-1', requestHash: 'hash-1' });
    await cache.putLatest(lastQuote);

    assert.deepEqual(await ingestVnsatPrices(deps), { rows: 0, inserted: 0 });
    assert.deepEqual(repo.rows.map((r) => r.quote.id), [lastQuote.id]);
    assert.deepEqual(cache.stored, [lastQuote]);
    assert.deepEqual(repo.failures, [{ commodity: 'COFFEE', reason: 'VNSAT unavailable' }]);
  });

  it('stores a published price once, with the source URL, and caches it after commit', async () => {
    const { repo, cache, deps } = setup(source([robustaDakLak], 'https://mirror.example/vnsat.aspx'));
    assert.deepEqual(await ingestVnsatPrices(deps), { rows: 1, inserted: 1 });
    assert.deepEqual(await ingestVnsatPrices(deps), { rows: 1, inserted: 0 });
    assert.equal(repo.rows.length, 1);
    const stored = repo.rows[0]!.quote;
    assert.equal(stored.source, 'VNSAT_SCRAPE');
    assert.equal(stored.provinceSlug, 'dak-lak');
    assert.equal(stored.referenceSource, 'https://mirror.example/vnsat.aspx');
    assert.deepEqual(cache.stored.map((q) => q.id), [stored.id]);
  });

  for (const [name, bad] of [
    ['a price that is not a VND/kg amount', { ...robustaGiaLai, price: '88.200' }],
    ['a zero price', { ...robustaGiaLai, price: '0.00' }],
    ['a date that does not exist', { ...robustaGiaLai, capturedAt: '2026-02-31' }],
    ['an empty province', { ...robustaGiaLai, province: ' ' }],
  ] as const) {
    it(`a row with ${name} in the middle of a batch leaves nothing written`, async () => {
      const { repo, cache, deps } = setup(source([robustaDakLak, bad, robustaLamDong]));
      assert.deepEqual(await ingestVnsatPrices(deps), { rows: 0, inserted: 0 });
      assert.equal(repo.rows.length, 0);
      assert.equal(cache.stored.length, 0);
      assert.match(repo.failures[0]!.reason, /source row 1/);
    });
  }

  it('a DB error in the middle of a batch commits none of it and keeps the old quote', async () => {
    const { repo, cache, deps } = setup(source([robustaDakLak, robustaGiaLai]));
    await repo.append({ quote: lastQuote, idempotencyKey: 'manual-1', requestHash: 'hash-1' });
    await cache.putLatest(lastQuote);
    repo.failBatchAtRow = 1;

    assert.deepEqual(await ingestVnsatPrices(deps), { rows: 0, inserted: 0 });
    assert.deepEqual(repo.rows.map((r) => r.quote.id), [lastQuote.id]);
    assert.deepEqual(cache.stored, [lastQuote]);
    assert.deepEqual(repo.failures, [{ commodity: 'COFFEE', reason: 'ER_LOCK_WAIT_TIMEOUT' }]);
  });

  it('replaying an already-stored row repairs a MISSING Redis entry', async () => {
    const { repo, cache, deps } = setup(source([robustaDakLak]));
    cache.fail = true; // first run: MySQL commits, the Redis write is lost
    await ingestVnsatPrices(deps);
    cache.fail = false;
    assert.equal(cache.stored.length, 0);

    assert.deepEqual(await ingestVnsatPrices(deps), { rows: 1, inserted: 0 });
    assert.deepEqual(cache.stored.map((q) => q.id), [repo.rows[0]!.quote.id]);
  });

  it('replaying an already-stored row repairs a STALE Redis entry', async () => {
    const { repo, cache, deps } = setup(source([robustaDakLak]));
    cache.fail = true;
    await ingestVnsatPrices(deps);
    cache.fail = false;
    await cache.putLatest(lastQuote); // Redis still holds the older Đắk Lắk quote

    await ingestVnsatPrices(deps);
    const cached = await cache.getLatest('dak-lak', 'ca-phe-robusta-nhan-xo');
    assert.equal(cached?.id, repo.rows[0]!.quote.id);
  });
});

describe('VnsatPriceIngestionJob (scheduler)', () => {
  it('never overlaps two runs: a tick while a run is in progress is skipped', async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const src = source(async () => { await gate; return [robustaDakLak]; });
    const { deps } = setup(src);
    const config = loadConfig({ GATEWAY_INTERNAL_SECRET: 's' });
    const job = new VnsatPriceIngestionJob(config, deps, memoryLogger(), src);

    const first = job.run();
    assert.equal(await job.run(), false); // second tick while the first is still waiting on the source
    release();
    assert.equal(await first, true);
    assert.equal(src.calls, 1);
    assert.equal(await job.run(), true); // the guard is released after the run
    assert.equal(src.calls, 2);
  });
});
