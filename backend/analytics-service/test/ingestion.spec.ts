import assert from 'node:assert/strict';
import type { ProjectionHandler } from '../src/application/port';
import { IngestEvent } from '../src/application/usecase/ingestEvent';
import { startIngestion } from '../src/infrastructure/messaging/consumers';
import {
  CONTRACTS_EXCHANGE,
  SUBSCRIPTIONS,
  deadLetterQueueName,
  declareTopology,
  queueName,
} from '../src/infrastructure/messaging/topology';
import { validEvent } from './fixtures';
import {
  FakeBroker,
  InMemoryDb,
  InMemoryIngestedEventStore,
  event,
  publishEvent,
  recordingLogger,
  silentLogger,
  type FakeTx,
} from './fakes';
import { deepEqual } from './deepEqual';

const SIGNED = 'contract.signed';
const SETTLED = 'milestone.settled';

function projection(eventType: string, opts: { failTimes?: number } = {}): ProjectionHandler<FakeTx> & { calls: number } {
  let failures = opts.failTimes ?? 0;
  const p = {
    eventType,
    calls: 0,
    async apply(_command: unknown, _tx: FakeTx) {
      p.calls += 1;
      if (failures > 0) {
        failures -= 1;
        throw new Error('projection deadlock');
      }
    },
  };
  return p;
}

describe('analytics canonical event ingestion', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;
  let store: InMemoryIngestedEventStore;
  let sleeps: number[];

  beforeEach(async () => {
    broker = new FakeBroker();
    db = new InMemoryDb();
    store = new InMemoryIngestedEventStore();
    sleeps = [];
    await declareTopology(broker);
  });

  async function start(projections: ProjectionHandler<FakeTx>[] = [], ingestStore = store) {
    return startIngestion({
      channel: broker,
      transactions: db,
      processedEvents: db,
      ingest: new IngestEvent(ingestStore, projections),
      logger: silentLogger,
      sleep: async (ms) => {
        sleeps.push(ms);
      },
    });
  }

  describe('a production consumer exists for every subscribed event, with no projection registered', () => {
    it('starts one consumer per subscribed queue', async () => {
      const handle = await start();
      assert.equal(handle.consumerTags.length, SUBSCRIPTIONS.length);
    });

    it('every subscribed event type is validated, persisted, marked processed and acked', async () => {
      await start();
      const published = SUBSCRIPTIONS.map((s, i) => event(s.eventType, `agg-${i}`));
      for (const env of published) publishEvent(broker, CONTRACTS_EXCHANGE, env);
      await broker.drain();
      deepEqual(
        db.committed.ingested.map((e) => e.eventId).sort(),
        published.map((e) => e.eventId).sort(),
      );
      assert.equal(db.committed.processed.length, SUBSCRIPTIONS.length);
      for (const { eventType } of SUBSCRIPTIONS) {
        assert.equal(broker.depth(queueName(eventType)), 0, eventType);
        assert.equal(broker.depth(deadLetterQueueName(eventType)), 0, eventType);
      }
      assert.equal(broker.unackedCount(), 0);
    });
  });

  describe('consumer dedupes events', () => {
    it('a redelivered event (same eventId) is persisted exactly once', async () => {
      await start();
      const env = event(SIGNED, 'c-1');
      publishEvent(broker, CONTRACTS_EXCHANGE, env);
      publishEvent(broker, CONTRACTS_EXCHANGE, env); // at-least-once redelivery
      await broker.drain();
      assert.equal(db.committed.ingested.length, 1);
      deepEqual(db.committed.processed, [
        { consumerName: 'analytics.contract.signed', eventId: env.eventId, eventType: SIGNED },
      ]);
      assert.equal(store.calls, 1);
    });

    it('different events for the same contract are all persisted', async () => {
      await start();
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      await broker.drain();
      assert.equal(db.committed.ingested.length, 2);
    });

    it('a failing write leaves no stored event and no idempotency row; the retry persists it once', async () => {
      const failing = new InMemoryIngestedEventStore(2);
      await start([], failing);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SETTLED, 'm-1'));
      await broker.drain();
      assert.equal(failing.calls, 3);
      assert.equal(db.committed.ingested.length, 1);
      assert.equal(db.committed.processed.length, 1);
      deepEqual(sleeps, [1000, 2000]); // transient errors back off, like the Java services
    });

    it('a failing projection rolls back the stored event too (one transaction)', async () => {
      const p = projection(SETTLED, { failTimes: 1 });
      await start([p]);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SETTLED, 'm-1'));
      await broker.drain();
      assert.equal(p.calls, 2);
      assert.equal(db.committed.ingested.length, 1);
      assert.equal(db.committed.processed.length, 1);
    });

    it('a projection only runs for its own event type', async () => {
      const p = projection(SETTLED);
      await start([p]);
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, 'c-1'));
      publishEvent(broker, CONTRACTS_EXCHANGE, event(SETTLED, 'm-1'));
      await broker.drain();
      assert.equal(p.calls, 1);
      assert.equal(db.committed.ingested.length, 2);
    });

    it('after retries are exhausted the event goes to its DLQ; replaying the original eventId persists once, replaying again is a no-op', async () => {
      const failing = new InMemoryIngestedEventStore(3);
      await start([], failing);
      const env = event(SETTLED, 'm-1');
      publishEvent(broker, CONTRACTS_EXCHANGE, env);
      await broker.drain();
      assert.equal(broker.depth(deadLetterQueueName(SETTLED)), 1);
      assert.equal(db.committed.ingested.length, 0);

      publishEvent(broker, CONTRACTS_EXCHANGE, env); // operator replays the original eventId
      publishEvent(broker, CONTRACTS_EXCHANGE, env); // ...twice by mistake
      await broker.drain();
      assert.equal(db.committed.ingested.length, 1);
      assert.equal(db.committed.processed.length, 1);
    });
  });

  describe('the ingestion boundary rejects invalid canonical input (review finding)', () => {
    async function assertRejected(deliver: () => void, queueType: string) {
      await start();
      deliver();
      await broker.drain();
      assert.equal(store.calls, 0, 'the ingest write must not run');
      assert.equal(db.committed.processed.length, 0, 'no processed marker');
      assert.equal(db.committed.ingested.length, 0);
      assert.equal(broker.depth(deadLetterQueueName(queueType)), 1, 'parked in the DLQ');
      deepEqual(sleeps, [], 'a permanent failure is never retried');
    }

    it('contract.signed from an unrelated producer with an empty payload (the review probe)', async () => {
      const bad = validEvent(SIGNED, 'c-1', { producer: 'unrelated-service', payload: {} });
      await assertRejected(() => publishEvent(broker, CONTRACTS_EXCHANGE, bad), SIGNED);
    });

    it('a payload with a bad amount', async () => {
      const bad = event(SETTLED, 'm-1', { lockedAmount: '12.5' });
      await assertRejected(() => publishEvent(broker, CONTRACTS_EXCHANGE, bad), SETTLED);
    });

    it('a payload with an unknown extra field', async () => {
      const bad = event(SIGNED, 'c-1', { surprise: true });
      await assertRejected(() => publishEvent(broker, CONTRACTS_EXCHANGE, bad), SIGNED);
    });

    it('an unsupported eventVersion', async () => {
      const bad = validEvent(SIGNED, 'c-1', { eventVersion: 9 });
      await assertRejected(() => publishEvent(broker, CONTRACTS_EXCHANGE, bad), SIGNED);
    });

    it('an off-contract envelope', async () => {
      await assertRejected(
        () => broker.publish(CONTRACTS_EXCHANGE, SIGNED, Buffer.from('{"eventType":"contract.signed"}')),
        SIGNED,
      );
    });

    it('an event that arrives on the wrong queue is dead-lettered, never guessed', async () => {
      const wrong = event('contract.settled', 'c-1');
      await assertRejected(() => broker.publish(CONTRACTS_EXCHANGE, SIGNED, Buffer.from(JSON.stringify(wrong))), SIGNED);
    });

    it('a valid event after a rejected one is still ingested', async () => {
      await start();
      publishEvent(broker, CONTRACTS_EXCHANGE, validEvent(SIGNED, 'c-1', { payload: {} }));
      const good = event(SIGNED, 'c-2');
      publishEvent(broker, CONTRACTS_EXCHANGE, good);
      await broker.drain();
      deepEqual(db.committed.ingested.map((e) => e.eventId), [good.eventId]);
    });
  });

  describe('contact PII is not stored', () => {
    it('recipient e-mail addresses of milestone.settled never reach the database', async () => {
      await start();
      const env = event(SETTLED, 'm-1');
      publishEvent(broker, CONTRACTS_EXCHANGE, env);
      await broker.drain();
      const stored = JSON.stringify(db.committed.ingested[0]!.payload);
      assert.doesNotMatch(stored, /@example\.com|email/);
      const recipients = (db.committed.ingested[0]!.payload as { recipients: Array<Record<string, unknown>> }).recipients;
      deepEqual(recipients.map((r) => Object.keys(r).sort()), [['role', 'userId'], ['role', 'userId']]);
    });
  });

  describe('outage does not block producers', () => {
    it('with analytics down, every producer publish succeeds and messages wait in durable queues', () => {
      for (let i = 0; i < 50; i++) {
        assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, `c-${i}`)), true);
      }
      assert.equal(broker.depth(queueName(SIGNED)), 50);
      assert.equal(db.committed.ingested.length, 0);
    });

    it('a slow or failing projection does not affect publishing', async () => {
      await start([projection(SIGNED, { failTimes: 1000 })]);
      for (let i = 0; i < 10; i++) assert.equal(publishEvent(broker, CONTRACTS_EXCHANGE, event(SIGNED, `c-${i}`)), true);
      await broker.drain();
      assert.equal(broker.depth(deadLetterQueueName(SIGNED)), 10);
    });
  });

  describe('queue backlog can catch up', () => {
    it('after a restart the whole backlog is persisted in order, exactly once, including duplicates', async () => {
      const envs = Array.from({ length: 100 }, (_, i) => event(SETTLED, `m-${String(i).padStart(3, '0')}`));
      for (const e of envs) publishEvent(broker, CONTRACTS_EXCHANGE, e);
      for (const e of envs.slice(0, 10)) publishEvent(broker, CONTRACTS_EXCHANGE, e); // duplicates in backlog
      assert.equal(broker.depth(queueName(SETTLED)), 110);

      await start(); // analytics comes back with NO projection registered
      await broker.drain();

      assert.equal(broker.depth(queueName(SETTLED)), 0);
      deepEqual(db.committed.ingested.map((e) => e.eventId), envs.map((e) => e.eventId));
      assert.equal(db.committed.processed.length, 100);
    });

    it('catch-up with a larger prefetch still applies each event exactly once', async () => {
      broker = new FakeBroker(20);
      await declareTopology(broker);
      const envs = Array.from({ length: 60 }, (_, i) => event(SETTLED, `m-${i}`));
      for (const e of [...envs, ...envs]) publishEvent(broker, CONTRACTS_EXCHANGE, e);
      await start();
      await broker.drain();
      assert.equal(db.committed.ingested.length, 60);
      assert.equal(new Set(db.committed.ingested.map((e) => e.eventId)).size, 60);
    });
  });

  describe('guards', () => {
    it('rejects a projection for an event type that has no declared queue', () => {
      assert.throws(() => new IngestEvent(store, [projection('bank.lock_requested')]), /No queue is declared/);
    });

    it('logs only event type and queue when a consumer starts', async () => {
      const logger = recordingLogger();
      await startIngestion({
        channel: broker,
        transactions: db,
        processedEvents: db,
        ingest: new IngestEvent(store),
        logger,
      });
      assert.equal(logger.entries.filter((e) => e.msg === 'Analytics consumer started').length, SUBSCRIPTIONS.length);
    });
  });
});
