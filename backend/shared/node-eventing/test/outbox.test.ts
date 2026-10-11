import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { createEnvelope, type EventEnvelope } from '../src/envelope.js';
import { InvalidEnvelopeError } from '../src/errors.js';
import { Outbox, OutboxPoller } from '../src/outbox.js';
import { FakeOutboxStore, FakePublisher, InMemoryDb, silentLogger, type FakeTx } from './fakes.js';

const EXCHANGE = 'agricontract.analytics';

function event(aggregateId: string): EventEnvelope {
  return createEnvelope({
    eventType: 'analytics.structuring_pattern_detected',
    producer: 'analytics-service',
    aggregateId,
    correlationId: 'job-1',
    payload: { aggregateId },
  });
}

describe('transactional outbox', () => {
  let db: InMemoryDb;
  let store: FakeOutboxStore;
  let outbox: Outbox<FakeTx>;
  let publisher: FakePublisher;
  let poller: OutboxPoller<FakeTx>;

  beforeEach(() => {
    db = new InMemoryDb();
    store = new FakeOutboxStore(db);
    outbox = new Outbox(store);
    publisher = new FakePublisher();
    poller = new OutboxPoller({ store, transactions: db, publisher, exchange: EXCHANGE, logger: silentLogger });
  });

  it('writes the event in the same transaction as the business change', async () => {
    const env = event('a-1');
    await db.inTransaction(async (tx) => {
      tx.state.business.push('fact-row');
      await outbox.enqueue(tx, env);
    });
    assert.deepEqual(db.committed.business, ['fact-row']);
    assert.equal(db.committed.outbox.length, 1);
    assert.equal(db.committed.outbox[0]?.status, 'PENDING');
    assert.deepEqual(db.committed.outbox[0]?.envelope, env);
  });

  it('a rolled-back business transaction leaves no event to publish', async () => {
    await assert.rejects(
      db.inTransaction(async (tx) => {
        await outbox.enqueue(tx, event('a-1'));
        throw new Error('business rule failed');
      }),
    );
    assert.equal(db.committed.outbox.length, 0);
    assert.deepEqual(await poller.pollOnce(), { published: 0, failed: 0 });
    assert.equal(publisher.sent.length, 0);
  });

  it('refuses off-contract envelopes at write time', async () => {
    const bad = { ...event('a-1'), eventType: 'NotDotted' };
    await assert.rejects(db.inTransaction((tx) => outbox.enqueue(tx, bad)), InvalidEnvelopeError);
    assert.equal(db.committed.outbox.length, 0);
  });

  it('poller only publishes events that are already persisted (persist before publish)', async () => {
    const env = event('a-1');
    await db.inTransaction((tx) => outbox.enqueue(tx, env));
    publisher.onPublish = (msg) => {
      const persisted = db.committed.outbox.find((r) => r.eventId === msg.messageId);
      assert.ok(persisted, 'published an event that is not committed in the outbox');
      assert.equal(persisted.status, 'PENDING', 'row must be marked PUBLISHED only after the broker confirms');
    };
    assert.deepEqual(await poller.pollOnce(), { published: 1, failed: 0 });
    assert.equal(db.committed.outbox[0]?.status, 'PUBLISHED');
  });

  it('publishes the full envelope with routing key = eventType and messageId = eventId', async () => {
    const env = event('a-1');
    await db.inTransaction((tx) => outbox.enqueue(tx, env));
    await poller.pollOnce();
    const msg = publisher.sent[0];
    assert.ok(msg);
    assert.equal(msg.exchange, EXCHANGE);
    assert.equal(msg.routingKey, env.eventType);
    assert.equal(msg.messageId, env.eventId);
    assert.equal(msg.type, env.eventType);
    assert.deepEqual(JSON.parse(msg.body.toString('utf8')), env);
  });

  it('keeps the row PENDING when the broker fails and publishes it on the next poll', async () => {
    const first = event('a-1');
    const second = event('a-2');
    await db.inTransaction(async (tx) => {
      await outbox.enqueue(tx, first);
      await outbox.enqueue(tx, second);
    });

    publisher.failNext = 1;
    assert.deepEqual(await poller.pollOnce(), { published: 0, failed: 1 });
    assert.equal(publisher.sent.length, 0, 'must stop at the first failure to keep order');
    const row = db.committed.outbox.find((r) => r.eventId === first.eventId);
    assert.equal(row?.status, 'PENDING');
    assert.equal(row?.attempts, 1);
    assert.equal(row?.lastError, 'broker unavailable');

    assert.deepEqual(await poller.pollOnce(), { published: 2, failed: 0 });
    assert.deepEqual(
      publisher.sent.map((m) => m.messageId),
      [first.eventId, second.eventId],
    );
  });

  it('does not publish the same row twice once it is PUBLISHED', async () => {
    await db.inTransaction((tx) => outbox.enqueue(tx, event('a-1')));
    await poller.pollOnce();
    await poller.pollOnce();
    assert.equal(publisher.sent.length, 1);
  });

  it('a duplicate eventId cannot be enqueued twice', async () => {
    const env = event('a-1');
    await db.inTransaction((tx) => outbox.enqueue(tx, env));
    await assert.rejects(db.inTransaction((tx) => outbox.enqueue(tx, env)));
    assert.equal(db.committed.outbox.length, 1);
  });

  it('respects batchSize', async () => {
    const small = new OutboxPoller({
      store,
      transactions: db,
      publisher,
      exchange: EXCHANGE,
      batchSize: 2,
      logger: silentLogger,
    });
    await db.inTransaction(async (tx) => {
      for (const id of ['a', 'b', 'c']) await outbox.enqueue(tx, event(id));
    });
    assert.equal((await small.pollOnce()).published, 2);
    assert.equal((await small.pollOnce()).published, 1);
  });

  it('monitor reports rows stuck PENDING longer than the threshold', async () => {
    await db.inTransaction((tx) => outbox.enqueue(tx, event('a-1')));
    assert.equal(await poller.checkStale(), 0);
    db.clockMs += 6 * 60_000;
    assert.equal(await poller.checkStale(), 1);
  });

  it('start/stop runs the polling loop', async () => {
    const fast = new OutboxPoller({
      store,
      transactions: db,
      publisher,
      exchange: EXCHANGE,
      pollIntervalMs: 5,
      logger: silentLogger,
    });
    await db.inTransaction((tx) => outbox.enqueue(tx, event('a-1')));
    fast.start();
    await new Promise((r) => setTimeout(r, 50));
    await fast.stop();
    assert.equal(publisher.sent.length, 1);
  });
});
