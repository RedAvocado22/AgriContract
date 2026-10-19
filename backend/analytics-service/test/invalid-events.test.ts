/**
 * Invalid canonical events (review of #163) through the production wiring: they go to the dead-letter
 * queue, are never retried, and leave no landing row and no dedup marker, so a corrected replay with the
 * same eventId is still ingested.
 */
import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { startAnalytics, type AnalyticsRuntime } from '../src/infrastructure/runtime.js';
import { CONTRACTS_EXCHANGE, deadLetterQueueName, queueName } from '../src/infrastructure/messaging/topology.js';
import { validEvent, validPayload } from './fixtures.js';
import { FakeBroker, InMemoryDb, InMemoryIngestedEventStore, publishEvent, recordingLogger, type FakeTx } from './fakes.js';

describe('invalid events are dead-lettered without side effects', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;
  let store: InMemoryIngestedEventStore;
  const runtimes: AnalyticsRuntime[] = [];

  beforeEach(async () => {
    for (const r of runtimes.splice(0)) r.stopMonitor();
    broker = new FakeBroker(20);
    db = new InMemoryDb();
    store = new InMemoryIngestedEventStore();
    runtimes.push(
      await startAnalytics<FakeTx>({
        consumeChannel: broker,
        monitorChannel: broker,
        transactions: db,
        processedEvents: db,
        store,
        projections: [],
        logger: recordingLogger(),
        dlqMonitorIntervalMs: 60_000,
        sleep: async () => {},
      }),
    );
  });

  const cases: Array<[string, string, Record<string, unknown>]> = [
    ['contract.signed', 'signedAt that does not exist in the calendar (2026-02-30)', { signedAt: '2026-02-30T00:00:00Z' }],
    ['contract.signed', 'hour 24', { signedAt: '2026-10-12T24:00:00Z' }],
    ['contract.settled', 'extra field named constructor', { constructor: 'x' }],
    ['contract.settled', 'extra field named toString', { toString: 'x' }],
  ];

  for (const [eventType, label, override] of cases) {
    it(`${eventType} with ${label}: DLQ, no landing row, no dedup marker, not retried`, async () => {
      const bad = validEvent(eventType, 'agg-1', { payload: { ...validPayload(eventType), ...override } });
      assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, bad), true);
      await broker.drain();

      assert.equal(broker.depth(deadLetterQueueName(eventType)), 1);
      assert.equal(broker.depth(queueName(eventType)), 0);
      assert.equal(broker.unackedCount(), 0);
      assert.equal(db.committed.ingested.length, 0, 'no landing row');
      assert.equal(db.committed.processed.length, 0, 'no dedup marker');
      assert.equal(store.calls, 0, 'the ingest use case never ran');
    });
  }

  it('the corrected event with the same eventId is then ingested (nothing was marked as processed)', async () => {
    const bad = validEvent('contract.signed', 'agg-1', { payload: { ...validPayload('contract.signed'), signedAt: '2026-02-30T00:00:00Z' } });
    publishEvent(broker, CONTRACTS_EXCHANGE, bad);
    await broker.drain();
    const fixed = { ...bad, payload: validPayload('contract.signed') };
    publishEvent(broker, CONTRACTS_EXCHANGE, fixed);
    await broker.drain();
    assert.deepEqual(db.committed.ingested.map((e) => e.eventId), [bad.eventId]);
    assert.equal(db.committed.processed.length, 1);
  });
});
