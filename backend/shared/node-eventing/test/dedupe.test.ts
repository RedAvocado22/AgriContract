import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { handleDelivery, type AmqpChannelLike, type AmqpMessageLike } from '../src/amqp.js';
import { IdempotentConsumer } from '../src/dedupe.js';
import { createEnvelope, type EventEnvelope } from '../src/envelope.js';
import { InvalidEnvelopeError, NonRetryableError } from '../src/errors.js';
import { FakeProcessedEventStore, InMemoryDb, silentLogger, type FakeTx } from './fakes.js';

function contractSigned(): EventEnvelope {
  return createEnvelope({
    eventType: 'contract.signed',
    producer: 'contract-service',
    aggregateId: 'c-1001',
    correlationId: 'saga-1',
    payload: { contractId: 'c-1001' },
  });
}

function message(env: unknown): AmqpMessageLike {
  const body = typeof env === 'string' ? env : JSON.stringify(env);
  return { content: Buffer.from(body), properties: { messageId: (env as EventEnvelope).eventId }, fields: {} };
}

class FakeChannel implements AmqpChannelLike {
  acks = 0;
  deadLettered = 0;
  async consume(): Promise<{ consumerTag: string }> {
    return { consumerTag: 'test' };
  }
  ack(): void {
    this.acks += 1;
  }
  nack(_m: AmqpMessageLike, _all?: boolean, requeue?: boolean): void {
    assert.equal(requeue, false, 'failed messages must go to the DLQ, not be requeued forever');
    this.deadLettered += 1;
  }
}

describe('consumer dedupe by eventId', () => {
  let db: InMemoryDb;
  let processed: FakeProcessedEventStore;
  let failuresLeft: number;
  let consumer: IdempotentConsumer<FakeTx>;

  beforeEach(() => {
    db = new InMemoryDb();
    processed = new FakeProcessedEventStore();
    failuresLeft = 0;
    consumer = new IdempotentConsumer<FakeTx>({
      consumerName: 'analytics.dim-contract',
      transactions: db,
      processedEvents: processed,
      handler: async (env, tx) => {
        tx.state.business.push(`upsert dim_contract ${env.aggregateId}`); // DB side effect
        if (failuresLeft > 0) {
          failuresLeft -= 1;
          throw new Error('deadlock');
        }
      },
    });
  });

  it('processes a new event once and records its eventId', async () => {
    const env = contractSigned();
    const outcome = await consumer.handle(env);
    assert.equal(outcome.status, 'PROCESSED');
    assert.equal(db.committed.business.length, 1);
    assert.deepEqual(db.committed.processed, [
      { consumerName: 'analytics.dim-contract', eventId: env.eventId, eventType: 'contract.signed' },
    ]);
  });

  it('skips a redelivery of the same eventId without repeating the side effect', async () => {
    const env = contractSigned();
    await consumer.handle(env);
    const again = await consumer.handle(structuredClone(env));
    assert.equal(again.status, 'DUPLICATE');
    assert.equal(db.committed.business.length, 1);
    assert.equal(db.committed.processed.length, 1);
  });

  it('a different event with the same payload is NOT a duplicate (key is eventId)', async () => {
    await consumer.handle(contractSigned());
    await consumer.handle(contractSigned());
    assert.equal(db.committed.business.length, 2);
  });

  it('dedupe is per consumer: two consumers each process the event once', async () => {
    const other = new IdempotentConsumer<FakeTx>({
      consumerName: 'notification.contract-signed',
      transactions: db,
      processedEvents: processed,
      handler: async (_env, tx) => {
        tx.state.business.push('email queued');
      },
    });
    const env = contractSigned();
    await consumer.handle(env);
    await other.handle(env);
    await other.handle(env);
    assert.deepEqual(db.committed.business, ['upsert dim_contract c-1001', 'email queued']);
  });

  it('handler failure rolls back the marker together with the side effect', async () => {
    failuresLeft = 1;
    await assert.rejects(consumer.handle(contractSigned()), /deadlock/);
    assert.equal(db.committed.business.length, 0);
    assert.equal(db.committed.processed.length, 0);
  });

  it('concurrent duplicate deliveries still apply the side effect exactly once', async () => {
    const env = contractSigned();
    const results = await Promise.all([consumer.handle(env), consumer.handle(env), consumer.handle(env)]);
    assert.deepEqual(results.map((r) => r.status).sort(), ['DUPLICATE', 'DUPLICATE', 'PROCESSED']);
    assert.equal(db.committed.business.length, 1);
  });

  describe('RabbitMQ delivery (retry -> DLQ -> replay)', () => {
    const noSleep = async () => {};

    it('acks processed and duplicate deliveries', async () => {
      const channel = new FakeChannel();
      const env = contractSigned();
      const opts = { channel, queue: 'q', consumer, logger: silentLogger, sleep: noSleep };
      assert.equal(await handleDelivery(message(env), opts), 'ACK_PROCESSED');
      assert.equal(await handleDelivery(message(env), opts), 'ACK_DUPLICATE');
      assert.equal(channel.acks, 2);
      assert.equal(db.committed.business.length, 1);
    });

    it('retries transient errors with backoff, then succeeds', async () => {
      const channel = new FakeChannel();
      const waits: number[] = [];
      failuresLeft = 2;
      const result = await handleDelivery(message(contractSigned()), {
        channel,
        queue: 'q',
        consumer,
        logger: silentLogger,
        sleep: async (ms) => void waits.push(ms),
      });
      assert.equal(result, 'ACK_PROCESSED');
      assert.deepEqual(waits, [1000, 2000]);
      assert.equal(db.committed.business.length, 1, 'failed attempts left no partial side effect');
    });

    it('Verification Matrix #7: DLQ replay of the original eventId has no repeated side effect', async () => {
      const channel = new FakeChannel();
      const env = contractSigned();
      const opts = { channel, queue: 'q', consumer, logger: silentLogger, sleep: noSleep };

      // 1) consumer keeps failing -> 3 attempts -> dead-lettered, nothing committed
      failuresLeft = 3;
      assert.equal(await handleDelivery(message(env), opts), 'DEAD_LETTERED');
      assert.equal(channel.deadLettered, 1);
      assert.equal(db.committed.processed.length, 0);
      assert.equal(db.committed.business.length, 0);

      // 2) OPERATOR replays the ORIGINAL eventId after the cause is fixed -> processed once
      assert.equal(await handleDelivery(message(env), opts), 'ACK_PROCESSED');
      assert.equal(db.committed.business.length, 1);

      // 3) replaying the same eventId again (already processed) -> skipped, no new entry
      assert.equal(await handleDelivery(message(env), opts), 'ACK_DUPLICATE');
      assert.equal(await handleDelivery(message(env), opts), 'ACK_DUPLICATE');
      assert.equal(db.committed.business.length, 1);
      assert.equal(db.committed.processed.length, 1);
    });

    it('invalid envelopes go straight to the DLQ without retry or handler call', async () => {
      const channel = new FakeChannel();
      const waits: number[] = [];
      const opts = {
        channel,
        queue: 'q',
        consumer,
        logger: silentLogger,
        sleep: async (ms: number) => void waits.push(ms),
      };
      assert.equal(await handleDelivery(message('{broken'), opts), 'DEAD_LETTERED');
      assert.equal(await handleDelivery(message({ ...contractSigned(), extra: true }), opts), 'DEAD_LETTERED');
      assert.equal(channel.deadLettered, 2);
      assert.deepEqual(waits, []);
      assert.equal(db.committed.business.length, 0);
    });

    it('NonRetryableError from a handler is dead-lettered immediately', async () => {
      const channel = new FakeChannel();
      let calls = 0;
      const strict = new IdempotentConsumer<FakeTx>({
        consumerName: 'file.scan',
        transactions: db,
        processedEvents: processed,
        handler: async () => {
          calls += 1;
          throw new NonRetryableError('referenced file does not exist');
        },
      });
      const result = await handleDelivery(message(contractSigned()), {
        channel,
        queue: 'q',
        consumer: strict,
        logger: silentLogger,
        sleep: noSleep,
      });
      assert.equal(result, 'DEAD_LETTERED');
      assert.equal(calls, 1);
      assert.equal(db.committed.processed.length, 0, 'not marked, so a fixed replay can still process it');
    });

    it('handleMessage surfaces InvalidEnvelopeError for callers not using handleDelivery', async () => {
      await assert.rejects(consumer.handleMessage(Buffer.from('[]')), InvalidEnvelopeError);
    });
  });
});
