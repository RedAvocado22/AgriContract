import {
  consumeEvents,
  handleDelivery,
  NonRetryableError,
  type AmqpChannelLike,
  type AmqpMessageLike,
  type DeliveryOutcome,
  type MessageConsumer,
} from '@agricontract/eventing';

/*
 * Guards the ESM/CJS setup from M0 (README "Why Jest has its own tsconfig"):
 * the real @agricontract/eventing must load and run under Jest.
 */

function recordingChannel() {
  const calls = {
    acks: 0,
    nacks: [] as Array<{ allUpTo?: boolean; requeue?: boolean }>,
  };
  const channel: AmqpChannelLike = {
    consume: () => Promise.resolve({ consumerTag: 'ctag' }),
    ack: () => void (calls.acks += 1),
    nack: (_msg, allUpTo, requeue) =>
      void calls.nacks.push({ allUpTo, requeue }),
  };
  return { channel, calls };
}

function consumer(handle: () => Promise<DeliveryOutcome>) {
  let attempts = 0;
  const c: MessageConsumer & { attempts: () => number } = {
    consumerName: 'test.smoke',
    attempts: () => attempts,
    handleMessage: () => {
      attempts += 1;
      return handle();
    },
  };
  return c;
}

const message: AmqpMessageLike = {
  content: Buffer.from('{"eventId":"evt-1"}'),
  fields: {},
};
const silent = { debug() {}, info() {}, warn() {}, error() {} };

describe('@agricontract/eventing under Jest', () => {
  it('loads the real package', () => {
    expect(typeof consumeEvents).toBe('function');
    expect(new NonRetryableError('x')).toBeInstanceOf(Error);
  });

  it('acks a PROCESSED outcome without an envelope', async () => {
    const { channel, calls } = recordingChannel();
    const c = consumer(() => Promise.resolve({ status: 'PROCESSED' }));

    const result = await handleDelivery(message, {
      channel,
      queue: 'q',
      consumer: c,
      logger: silent,
    });

    expect(result).toBe('ACK_PROCESSED');
    expect(calls.acks).toBe(1);
    expect(calls.nacks).toEqual([]);
  });

  it('dead-letters a NonRetryableError at once, without retrying', async () => {
    const { channel, calls } = recordingChannel();
    const c = consumer(() => Promise.reject(new NonRetryableError('bad')));

    const result = await handleDelivery(message, {
      channel,
      queue: 'q',
      consumer: c,
      logger: silent,
      sleep: () => Promise.resolve(),
    });

    expect(result).toBe('DEAD_LETTERED');
    expect(c.attempts()).toBe(1);
    expect(calls.acks).toBe(0);
    expect(calls.nacks).toEqual([{ allUpTo: false, requeue: false }]);
  });
});
