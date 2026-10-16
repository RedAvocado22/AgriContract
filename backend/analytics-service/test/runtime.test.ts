/**
 * Production wiring (startAnalytics, the same function main.ts calls) exercised with a stop / publish /
 * restart / replay scenario. Only the broker and the database are in-memory fakes.
 */
import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { startAnalytics, type AnalyticsRuntime } from '../src/infrastructure/runtime.js';
import {
  CONTRACTS_EXCHANGE,
  SUBSCRIPTIONS,
  deadLetterQueueName,
  queueName,
} from '../src/infrastructure/messaging/topology.js';
import { validEvent } from './fixtures.js';
import {
  FakeBroker,
  InMemoryDb,
  InMemoryIngestedEventStore,
  event,
  publishEvent,
  recordingLogger,
} from './fakes.js';

describe('production wiring: stop, publish, restart, replay', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;
  const logger = recordingLogger();
  const runtimes: AnalyticsRuntime[] = [];

  beforeEach(() => {
    broker = new FakeBroker(20);
    db = new InMemoryDb();
    logger.entries.length = 0;
    for (const r of runtimes.splice(0)) r.stopMonitor();
  });

  async function boot(): Promise<AnalyticsRuntime> {
    const runtime = await startAnalytics({
      consumeChannel: broker,
      monitorChannel: broker,
      transactions: db,
      processedEvents: db,
      store: new InMemoryIngestedEventStore(),
      projections: [], // exactly what main.ts registers in this increment
      logger,
      dlqMonitorIntervalMs: 60_000,
      sleep: async () => {},
    });
    runtimes.push(runtime);
    return runtime;
  }

  it('events published while analytics is stopped are all ingested exactly once after the restart', async () => {
    const first = await boot();
    await first.stopConsuming(); // analytics stops (queues and DLQs stay declared)
    const stoppedBefore = db.committed.ingested.length;

    const events = SUBSCRIPTIONS.flatMap(({ eventType }, i) =>
      Array.from({ length: 5 }, (_, j) => event(eventType, `agg-${i}-${j}`)),
    );
    for (const e of events) assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, e), true); // producers never block
    for (const e of events.slice(0, 7)) publishEvent(broker, CONTRACTS_EXCHANGE, e); // duplicates in the backlog
    assert.equal(db.committed.ingested.length, stoppedBefore);
    assert.equal(SUBSCRIPTIONS.reduce((n, s) => n + broker.depth(queueName(s.eventType)), 0), events.length + 7);

    await boot(); // restart
    await broker.drain();

    assert.deepEqual(
      db.committed.ingested.map((e) => e.eventId).sort(),
      events.map((e) => e.eventId).sort(),
    );
    assert.equal(db.committed.processed.length, events.length);
    for (const { eventType } of SUBSCRIPTIONS) {
      assert.equal(broker.depth(queueName(eventType)), 0, eventType);
      assert.equal(broker.depth(deadLetterQueueName(eventType)), 0, eventType);
    }
    assert.equal(broker.unackedCount(), 0);
  });

  it('replaying the same eventIds after the restart is a no-op; a DLQ replay of a fixed event is ingested once', async () => {
    await boot();
    const good = event('contract.signed', 'c-1');
    publishEvent(broker, CONTRACTS_EXCHANGE, good);
    const bad = validEvent('contract.signed', 'c-2', { payload: {} });
    publishEvent(broker, CONTRACTS_EXCHANGE, bad);
    await broker.drain();
    assert.equal(db.committed.ingested.length, 1);
    assert.equal(broker.depth(deadLetterQueueName('contract.signed')), 1);

    await boot(); // another restart
    publishEvent(broker, CONTRACTS_EXCHANGE, good);
    publishEvent(broker, CONTRACTS_EXCHANGE, good);
    await broker.drain();
    assert.equal(db.committed.ingested.length, 1);
    assert.equal(db.committed.processed.length, 1);
  });

  it('startup declares the topology itself and starts the DLQ monitor', async () => {
    await boot();
    for (const { eventType } of SUBSCRIPTIONS) {
      assert.ok(broker.queues.has(queueName(eventType)));
      assert.ok(broker.queues.has(deadLetterQueueName(eventType)));
    }
    await new Promise((r) => setTimeout(r, 10));
    assert.equal(broker.checkCalls, SUBSCRIPTIONS.length, 'the monitor checked every DLQ at startup');
    assert.equal(logger.entries.filter((e) => e.level === 'warn').length, 0, 'empty DLQs produce no warning');
  });

  it('a rejected event shows up as a DLQ warning with queue and depth only', async () => {
    const runtime = await boot();
    publishEvent(broker, CONTRACTS_EXCHANGE, validEvent('contract.settled', 'c-3', { payload: { contractId: 'x' } }));
    await broker.drain();
    runtime.stopMonitor();
    const { DlqMonitor } = await import('../src/infrastructure/messaging/dlqMonitor.js');
    await new DlqMonitor(broker, { intervalMs: 60_000, logger }).checkOnce();
    const warning = logger.entries.find((e) => e.msg.startsWith('Dead-letter queue is not empty'));
    assert.deepEqual(warning?.meta, { queue: deadLetterQueueName('contract.settled'), depth: 1 });
  });
});
