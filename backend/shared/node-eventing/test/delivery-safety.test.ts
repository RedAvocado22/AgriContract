/**
 * Review #156 [MAJOR] (rules §4B: no floating promise / unhandled rejection).
 * amqplib throws from ack()/nack() once the channel is closed ("Channel closed"). That must never
 * escape as an unhandled rejection (Node >= 15 would crash the whole service) and must not make the
 * handler run again. The message simply stays unacked: the broker redelivers it on a new channel
 * and consumer dedupe drops the repeat.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { consumeEvents, handleDelivery, type AmqpChannelLike, type AmqpMessageLike, type MessageConsumer } from '../src/amqp.js';
import { createEnvelope, type EventEnvelope } from '../src/envelope.js';
import { NonRetryableError } from '../src/errors.js';
import type { Logger } from '../src/ports.js';

/** Like an amqplib channel after the connection dropped: ack/nack throw synchronously. */
class ClosedChannel implements AmqpChannelLike {
  onMessage: ((m: AmqpMessageLike | null) => void) | undefined;
  async consume(_queue: string, onMessage: (m: AmqpMessageLike | null) => void) {
    this.onMessage = onMessage;
    return { consumerTag: 'ctag-closed' };
  }
  ack(): void {
    throw new Error('Channel closed');
  }
  nack(): void {
    throw new Error('Channel closed');
  }
}

function recordingLogger(): Logger & { errors: string[] } {
  const errors: string[] = [];
  return { errors, debug() {}, info() {}, warn() {}, error: (m) => void errors.push(m) };
}

function envelope(): EventEnvelope {
  return createEnvelope({ eventType: 'contract.signed', producer: 'contract-service', aggregateId: 'c-1', payload: {} });
}

function message(env: EventEnvelope): AmqpMessageLike {
  return { content: Buffer.from(JSON.stringify(env)), properties: { messageId: env.eventId }, fields: {} };
}

function countingConsumer(behaviour: () => Promise<void>): MessageConsumer & { calls: number } {
  const env = envelope();
  const c = {
    consumerName: 'test.consumer',
    calls: 0,
    async handleMessage() {
      c.calls += 1;
      await behaviour();
      return { status: 'PROCESSED' as const, envelope: env };
    },
  };
  return c;
}

/** Runs `work` and returns every unhandled rejection raised meanwhile. */
async function unhandledRejectionsDuring(work: () => Promise<void>): Promise<unknown[]> {
  const seen: unknown[] = [];
  const listener = (reason: unknown) => void seen.push(reason);
  process.on('unhandledRejection', listener);
  try {
    await work();
    await new Promise((r) => setTimeout(r, 50));
  } finally {
    process.off('unhandledRejection', listener);
  }
  return seen;
}

describe('a closed channel never crashes the consumer (review #156)', () => {
  it('ack throws after processing: no unhandled rejection, handler not re-run, error logged', async () => {
    const channel = new ClosedChannel();
    const consumer = countingConsumer(async () => {});
    const logger = recordingLogger();
    const rejections = await unhandledRejectionsDuring(async () => {
      await consumeEvents({ channel, queue: 'q', consumer, logger, sleep: async () => {} });
      channel.onMessage!(message(envelope()));
    });
    assert.deepEqual(rejections, []);
    assert.equal(consumer.calls, 1, 'a failed ack must not be treated as a handler failure');
    assert.ok(logger.errors.length > 0);
  });

  it('nack throws on the DLQ path: no unhandled rejection, error logged', async () => {
    const channel = new ClosedChannel();
    const consumer = countingConsumer(async () => {
      throw new NonRetryableError('bad payload');
    });
    const logger = recordingLogger();
    const rejections = await unhandledRejectionsDuring(async () => {
      await consumeEvents({ channel, queue: 'q', consumer, logger, sleep: async () => {} });
      channel.onMessage!(message(envelope()));
    });
    assert.deepEqual(rejections, []);
    assert.equal(consumer.calls, 1);
    assert.ok(logger.errors.length > 0);
  });

  it('handleDelivery resolves UNSETTLED instead of rejecting when ack or nack throws', async () => {
    const channel = new ClosedChannel();
    const logger = recordingLogger();
    const ok = countingConsumer(async () => {});
    assert.equal(await handleDelivery(message(envelope()), { channel, queue: 'q', consumer: ok, logger }), 'UNSETTLED');
    const transient = countingConsumer(async () => {
      throw new Error('deadlock');
    });
    const result = await handleDelivery(message(envelope()), {
      channel,
      queue: 'q',
      consumer: transient,
      logger,
      maxAttempts: 2,
      sleep: async () => {},
    });
    assert.equal(result, 'UNSETTLED');
    assert.equal(transient.calls, 2, 'transient errors are still retried before dead-lettering');
  });

  it('consumeEvents logs instead of crashing if delivery handling itself fails', async () => {
    const channel = new ClosedChannel();
    const consumer = countingConsumer(async () => {
      throw new Error('deadlock');
    });
    const logger = recordingLogger();
    const rejections = await unhandledRejectionsDuring(async () => {
      await consumeEvents({
        channel,
        queue: 'q',
        consumer,
        logger,
        sleep: async () => {
          throw new Error('timer failure'); // anything unexpected inside the delivery loop
        },
      });
      channel.onMessage!(message(envelope()));
    });
    assert.deepEqual(rejections, []);
    assert.ok(logger.errors.some((m) => /Delivery handling failed/.test(m)));
  });
});
