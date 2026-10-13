import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import type { IngestionHandler } from '../src/application/port.js';
import { startIngestion } from '../src/infrastructure/messaging/consumers.js';
import { CONTRACTS_EXCHANGE, SUBSCRIPTIONS, deadLetterQueueName, declareTopology, queueName } from '../src/infrastructure/messaging/topology.js';
import { FakeBroker, InMemoryDb, event, publishEvent, silentLogger, type FakeTx } from './fakes.js';

const SIGNED = 'contract.signed';
const SETTLED = 'milestone.settled';

function recordingHandler(eventType: string, opts: { failTimes?: number } = {}): IngestionHandler<FakeTx> & { calls: number } {
  let failures = opts.failTimes ?? 0;
  const h = {
    eventType,
    calls: 0,
    async handle(env: { aggregateId: string; eventType: string }, tx: FakeTx) {
      h.calls += 1;
      tx.state.facts.push(`${env.eventType}:${env.aggregateId}`); // projection write inside the tx
      if (failures > 0) {
        failures -= 1;
        throw new Error('deadlock');
      }
    },
  };
  return h;
}

async function start(broker: FakeBroker, db: InMemoryDb, handlers: IngestionHandler<FakeTx>[]) {
  return startIngestion({
    channel: broker,
    transactions: db,
    processedEvents: db,
    handlers,
    logger: silentLogger,
    sleep: async () => {},
  });
}

describe('analytics canonical event ingestion', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;

  beforeEach(async () => {
    broker = new FakeBroker();
    db = new InMemoryDb();
    await declareTopology(broker);
  });

  describe('consumer dedupes events', () => {
    it('a redelivered event (same eventId) is projected exactly once', async () => {
      await start(broker, db, [recordingHandler(SIGNED)]);
      const env = event(SIGNED, 'c-1');
      publishEvent(broker, CONTRACTS_EXCHANGE, env);
      publishEvent(broker, CONTRACTS_EXCHANGE, env); // at-least-once redelivery
      await broker.drain();
      assert.deepEqual(db.committed.facts, ['contract.signed:c-1']);
      assert.deepEqual(db.committed.processed, [
        { consumerName: 'analytics.contract.signed', eventId: env.eventId, eventType: SIGNED },
      ]);
    });

    it('different events for the same contract are all projected', async () => {
      await start(broker, db, [recordingHandler(SIGNED)]);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      await broker.drain();
      assert.equal(db.committed.facts.length, 2);
    });

    it('a failing handler leaves no fact and no idempotency row; the retry projects it once', async () => {
      const h = recordingHandler(SETTLED, { failTimes: 2 });
      await start(broker, db, [h]);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SETTLED, 'm-1'));
      await broker.drain();
      assert.equal(h.calls, 3);
      assert.deepEqual(db.committed.facts, ['milestone.settled:m-1']);
      assert.equal(db.committed.processed.length, 1);
    });

    it('after retries are exhausted the event goes to its DLQ; replaying the original eventId projects once, replaying again is a no-op', async () => {
      const h = recordingHandler(SETTLED, { failTimes: 3 });
      await start(broker, db, [h]);
      const env = event(SETTLED, 'm-1');
      publishEvent(broker, CONTRACTS_EXCHANGE, env);
      await broker.drain();
      assert.equal(broker.depth(deadLetterQueueName(SETTLED)), 1);
      assert.equal(db.committed.facts.length, 0);

      publishEvent(broker, CONTRACTS_EXCHANGE, env); // operator replays the original eventId
      publishEvent(broker, CONTRACTS_EXCHANGE, env); // ...twice by mistake
      await broker.drain();
      assert.deepEqual(db.committed.facts, ['milestone.settled:m-1']);
    });

    it('an off-contract envelope is dead-lettered without calling the handler', async () => {
      const h = recordingHandler(SIGNED);
      await start(broker, db, [h]);
      broker.publish(CONTRACTS_EXCHANGE, SIGNED, Buffer.from('{"eventType":"contract.signed"}'));
      await broker.drain();
      assert.equal(h.calls, 0);
      assert.equal(broker.depth(deadLetterQueueName(SIGNED)), 1);
    });

    it('an event that arrives on the wrong queue is dead-lettered, never guessed', async () => {
      const h = recordingHandler(SIGNED);
      await start(broker, db, [h]);
      const wrong = event('contract.settled', 'c-1');
      broker.publish(CONTRACTS_EXCHANGE, SIGNED, Buffer.from(JSON.stringify(wrong)));
      await broker.drain();
      assert.equal(db.committed.facts.length, 0);
      assert.equal(broker.depth(deadLetterQueueName(SIGNED)), 1);
    });
  });

  describe('outage does not block producers', () => {
    it('with analytics down, every producer publish succeeds and messages wait in durable queues', async () => {
      // analytics-service not started: no consumer at all
      for (let i = 0; i < 50; i++) {
        assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, `c-${i}`)), true);
      }
      assert.equal(broker.depth(queueName(SIGNED)), 50);
      assert.equal(db.committed.facts.length, 0);
    });

    it('a slow or failing projection does not affect publishing', async () => {
      await start(broker, db, [recordingHandler(SIGNED, { failTimes: 1000 })]);
      for (let i = 0; i < 10; i++) assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, `c-${i}`)), true);
      await broker.drain();
      assert.equal(broker.depth(deadLetterQueueName(SIGNED)), 10);
    });
  });

  describe('queue backlog can catch up', () => {
    it('after a restart the whole backlog is projected in order, exactly once, including duplicates', async () => {
      const envs = Array.from({ length: 100 }, (_, i) => event(SETTLED, `m-${String(i).padStart(3, '0')}`));
      for (const e of envs) publishEvent(broker, CONTRACTS_EXCHANGE, e);
      for (const e of envs.slice(0, 10)) publishEvent(broker, CONTRACTS_EXCHANGE, e); // duplicates in backlog
      assert.equal(broker.depth(queueName(SETTLED)), 110);

      await start(broker, db, [recordingHandler(SETTLED)]); // analytics comes back
      await broker.drain();

      assert.equal(broker.depth(queueName(SETTLED)), 0);
      assert.deepEqual(db.committed.facts, envs.map((e) => `milestone.settled:${e.aggregateId}`));
      assert.equal(db.committed.processed.length, 100);
    });

    it('catch-up with a larger prefetch still applies each event exactly once', async () => {
      broker = new FakeBroker(20);
      await declareTopology(broker);
      const envs = Array.from({ length: 60 }, (_, i) => event(SETTLED, `m-${i}`));
      for (const e of [...envs, ...envs]) publishEvent(broker, CONTRACTS_EXCHANGE, e);
      await start(broker, db, [recordingHandler(SETTLED)]);
      await broker.drain();
      assert.equal(db.committed.facts.length, 60);
      assert.equal(new Set(db.committed.facts).size, 60);
    });

    it('subscribed events without a handler yet keep buffering instead of being acked and lost', async () => {
      await start(broker, db, [recordingHandler(SIGNED)]);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      publishEvent(broker, CONTRACTS_EXCHANGE, event('contract.terminated', 'c-2'));
      await broker.drain();
      assert.deepEqual(db.committed.facts, ['contract.signed:c-1']);
      assert.equal(broker.depth(queueName('contract.terminated')), 1);

      // later release ships the handler: buffered event is caught up
      await start(broker, db, [recordingHandler('contract.terminated')]);
      await broker.drain();
      assert.deepEqual(db.committed.facts, ['contract.signed:c-1', 'contract.terminated:c-2']);
    });
  });

  describe('startIngestion guards', () => {
    it('rejects a handler for an event type that has no declared queue', async () => {
      await assert.rejects(start(broker, db, [recordingHandler('bank.lock_requested')]), /No queue is declared/);
    });

    it('rejects two handlers for the same event type', async () => {
      await assert.rejects(start(broker, db, [recordingHandler(SIGNED), recordingHandler(SIGNED)]), /Duplicate handler/);
    });

    it('starts one consumer per handler', async () => {
      const tags = await start(broker, db, SUBSCRIPTIONS.map((s) => recordingHandler(s.eventType)));
      assert.equal(tags.length, SUBSCRIPTIONS.length);
    });
  });
});
