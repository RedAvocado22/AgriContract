import { Logger } from '@nestjs/common';
import {
  NonRetryableError,
  handleDelivery,
  type AmqpChannelLike,
  type AmqpMessageLike,
} from '@agricontract/eventing';
import type { Channel } from 'amqplib';
import { legacyRoutes, type NotificationUseCases } from './legacy-routes';
import type { AmqpService, ChannelReadyCallback } from './amqp.provider';
import {
  DRAIN_TIMEOUT_MS,
  NotificationEventConsumer,
  QueueConsumer,
} from './notification-event.consumer';
import { queueNames } from './topology';

type OnMessage = (msg: AmqpMessageLike | null) => void;

/** amqplib channel stand-in: records consumers, acks, nacks and cancels. */
class FakeChannel {
  readonly consumers = new Map<string, OnMessage>();
  readonly acked: AmqpMessageLike[] = [];
  readonly nacked: Array<{ msg: AmqpMessageLike; requeue?: boolean }> = [];
  consume = jest.fn((queue: string, onMessage: OnMessage) => {
    this.consumers.set(queue, onMessage);
    return Promise.resolve({ consumerTag: `tag:${queue}` });
  });
  ack = jest.fn((msg: AmqpMessageLike) => void this.acked.push(msg));
  nack = jest.fn(
    (msg: AmqpMessageLike, _all?: boolean, requeue?: boolean) =>
      void this.nacked.push({ msg, requeue }),
  );
  cancel = jest.fn(() => Promise.resolve({}));

  deliver(queue: string, payload: unknown): AmqpMessageLike {
    const msg: AmqpMessageLike = {
      content: Buffer.from(
        typeof payload === 'string' ? payload : JSON.stringify(payload),
      ),
      fields: { routingKey: queue.replace('notification-svc.', '') },
    };
    const onMessage = this.consumers.get(queue);
    if (!onMessage) throw new Error(`no consumer on ${queue}`);
    onMessage(msg);
    return msg;
  }
}

const HANDLERS = [
  'contractSigned',
  'contractCancelled',
  'contractDelivered',
  'contractDisputed',
  'escrowLocked',
  'escrowReleased',
  'escrowPenalized',
  'categoryApproved',
  'categoryRejected',
] as const;

function setup() {
  const readyCallbacks: ChannelReadyCallback[] = [];
  const amqp = {
    onChannelReady: jest.fn((cb: ChannelReadyCallback) => {
      readyCallbacks.push(cb);
      return Promise.resolve();
    }),
  } as unknown as AmqpService;
  const service = Object.fromEntries(
    HANDLERS.map((h) => [
      h,
      jest.fn<Promise<void>, [Record<string, unknown>]>(() =>
        Promise.resolve(),
      ),
    ]),
  ) as Record<
    (typeof HANDLERS)[number],
    jest.Mock<Promise<void>, [Record<string, unknown>]>
  >;
  // Fake use cases: execute() is the mock, routed through the real legacyRoutes.
  const useCases = Object.fromEntries(
    HANDLERS.map((h) => [h, { execute: service[h] }]),
  ) as unknown as NotificationUseCases;
  const consumer = new NotificationEventConsumer(amqp, legacyRoutes(useCases));
  const open = async (channel: FakeChannel) => {
    for (const cb of readyCallbacks) await cb(channel as unknown as Channel);
  };
  return { consumer, service, open };
}

/** Lets the delivery promises chained by consumeEvents run. */
const flush = async () => {
  for (let i = 0; i < 10; i += 1) await new Promise((r) => setImmediate(r));
};

const PAYLOADS: Record<
  string,
  { handler: (typeof HANDLERS)[number]; payload: Record<string, unknown> }
> = {
  'notification-svc.contract.signed': {
    handler: 'contractSigned',
    payload: {
      eventId: 'e1',
      contractId: 'c',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
  },
  'notification-svc.contract.cancelled': {
    handler: 'contractCancelled',
    payload: {
      eventId: 'e2',
      contractId: 'c',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      reason: 'r',
    },
  },
  'notification-svc.contract.delivered': {
    handler: 'contractDelivered',
    payload: { eventId: 'e3', contractId: 'c', sellerEmail: 's@x.test' },
  },
  'notification-svc.contract.disputed': {
    handler: 'contractDisputed',
    payload: {
      eventId: 'e4',
      contractId: 'c',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
  },
  'notification-svc.escrow.locked': {
    handler: 'escrowLocked',
    payload: { eventId: 'e5', escrowId: 'x', sellerEmail: 's@x.test' },
  },
  'notification-svc.escrow.released': {
    handler: 'escrowReleased',
    payload: {
      eventId: 'e6',
      escrowId: 'x',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
  },
  'notification-svc.escrow.penalized': {
    handler: 'escrowPenalized',
    payload: {
      eventId: 'e7',
      escrowId: 'x',
      penalizedParty: 'SELLER',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
  },
  'notification-svc.category.approved': {
    handler: 'categoryApproved',
    payload: { eventId: 'e8', name: 'n', proposedByEmail: 'p@x.test' },
  },
  'notification-svc.category.rejected': {
    handler: 'categoryRejected',
    payload: {
      eventId: 'e9',
      name: 'n',
      proposedByEmail: 'p@x.test',
      rejectionReason: 'dup',
    },
  },
};

describe('NotificationEventConsumer', () => {
  let warn: jest.SpyInstance;

  beforeEach(() => {
    for (const level of ['log', 'error', 'debug'] as const) {
      jest.spyOn(Logger.prototype, level).mockImplementation(() => undefined);
    }
    warn = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
  });
  afterEach(() => jest.restoreAllMocks());

  it('registers through AmqpService.onChannelReady at startup', async () => {
    const { consumer } = setup();
    await consumer.onModuleInit();
    // consumers are attached only when a channel is ready
    expect(consumer.inFlightCount).toBe(0);
  });

  it('consumes the 9 queues on every channel, again after a reconnect', async () => {
    const { consumer, open } = setup();
    await consumer.onModuleInit();
    const first = new FakeChannel();
    const second = new FakeChannel();

    await open(first);
    await open(second);

    for (const channel of [first, second]) {
      expect(channel.consume).toHaveBeenCalledTimes(9);
      expect([...channel.consumers.keys()].sort()).toEqual(queueNames().sort());
      for (const call of channel.consume.mock.calls as unknown[][]) {
        expect(call[2]).toEqual({ noAck: false });
      }
    }
  });

  it.each(Object.entries(PAYLOADS))(
    '%s runs its handler with the parsed command and acks',
    async (queue, { handler, payload }) => {
      const { consumer, service, open } = setup();
      await consumer.onModuleInit();
      const channel = new FakeChannel();
      await open(channel);

      const msg = channel.deliver(queue, { ...payload, eventType: 'ignored' });
      await flush();

      expect(service[handler]).toHaveBeenCalledTimes(1);
      expect(service[handler].mock.calls[0][0]).toMatchObject({
        eventId: payload.eventId,
      });
      for (const other of HANDLERS.filter((h) => h !== handler)) {
        expect(service[other]).not.toHaveBeenCalled();
      }
      expect(channel.acked).toEqual([msg]);
      expect(channel.nacked).toEqual([]);
      expect(consumer.inFlightCount).toBe(0);
    },
  );

  it('dead-letters a malformed payload at once, without calling the handler', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver('notification-svc.contract.signed', '{oops');
    await flush();

    expect(service.contractSigned).not.toHaveBeenCalled();
    expect(channel.nacked).toEqual([{ msg, requeue: false }]);
    expect(channel.acked).toEqual([]);
  });

  it('on shutdown cancels every consumer, then waits for in-flight messages', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);
    let finish!: () => void;
    service.escrowLocked.mockImplementation(
      () => new Promise<void>((resolve) => (finish = resolve)),
    );

    const msg = channel.deliver(
      'notification-svc.escrow.locked',
      PAYLOADS['notification-svc.escrow.locked'].payload,
    );
    await flush();
    expect(consumer.inFlightCount).toBe(1);

    let drained = false;
    const shutdown = consumer
      .beforeApplicationShutdown()
      .then(() => (drained = true));
    await flush();

    expect(channel.cancel).toHaveBeenCalledTimes(9);
    expect(channel.cancel).toHaveBeenCalledWith(
      'tag:notification-svc.escrow.locked',
    );
    expect(drained).toBe(false); // still waiting for the message
    expect(channel.acked).toEqual([]);

    finish();
    await shutdown;

    expect(channel.acked).toEqual([msg]);
    expect(consumer.inFlightCount).toBe(0);
  });

  it('drains for 40s: above the 30s SMTP socket timeout, below the 60s compose stop_grace_period', () => {
    expect(DRAIN_TIMEOUT_MS).toBe(40_000);
  });

  describe('drain timing (fake timers)', () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    /** One escrow.locked message in flight whose handler runs until `finish()`. */
    async function oneInFlight() {
      const { consumer, service, open } = setup();
      await consumer.onModuleInit();
      const channel = new FakeChannel();
      await open(channel);
      let finish!: () => void;
      service.escrowLocked.mockImplementation(
        () => new Promise<void>((resolve) => (finish = resolve)),
      );
      const msg = channel.deliver(
        'notification-svc.escrow.locked',
        PAYLOADS['notification-svc.escrow.locked'].payload,
      );
      await jest.advanceTimersByTimeAsync(0);
      let drained = false;
      const shutdown = consumer
        .beforeApplicationShutdown()
        .then(() => (drained = true));
      return {
        consumer,
        channel,
        msg,
        shutdown,
        finish: () => finish(),
        drained: () => drained,
      };
    }

    it('returns as soon as the last message is acked, long before the timeout', async () => {
      const { channel, msg, shutdown, finish, drained } = await oneInFlight();

      await jest.advanceTimersByTimeAsync(25_000); // e.g. a slow SMTP send
      expect(drained()).toBe(false);

      finish();
      await jest.advanceTimersByTimeAsync(0);
      await shutdown;

      expect(drained()).toBe(true);
      expect(channel.acked).toEqual([msg]);
      expect(warn).not.toHaveBeenCalledWith(
        expect.stringContaining('still in flight'),
      );
    });

    it('stops waiting after exactly the drain timeout and leaves the message unacked', async () => {
      const { consumer, channel, shutdown, drained } = await oneInFlight();

      await jest.advanceTimersByTimeAsync(DRAIN_TIMEOUT_MS - 1);
      expect(drained()).toBe(false);

      await jest.advanceTimersByTimeAsync(1);
      await shutdown;

      // Neither acked nor nacked: the channel closes next (AmqpService) and
      // the broker redelivers the message.
      expect(channel.acked).toEqual([]);
      expect(channel.nacked).toEqual([]);
      expect(consumer.inFlightCount).toBe(1);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('still in flight after 40000ms'),
      );
    });
  });

  it('does not attach to a channel that opens during shutdown', async () => {
    const { consumer, open } = setup();
    await consumer.onModuleInit();
    await consumer.beforeApplicationShutdown();

    const late = new FakeChannel();
    await open(late);

    expect(late.consume).not.toHaveBeenCalled();
  });
});

describe('delivery handling of a QueueConsumer (eventing handleDelivery)', () => {
  function recordingChannel() {
    const calls = { acks: 0, nacks: [] as Array<boolean | undefined> };
    const channel: AmqpChannelLike = {
      consume: () => Promise.resolve({ consumerTag: 't' }),
      ack: () => void (calls.acks += 1),
      nack: (_msg, _all, requeue) => void calls.nacks.push(requeue),
    };
    return { channel, calls };
  }
  const msg: AmqpMessageLike = { content: Buffer.from('{}'), fields: {} };
  const silent = { debug() {}, info() {}, warn() {}, error() {} };

  it('retries a transient error 3 times with 1s/2s backoff, then nacks without requeue', async () => {
    const { channel, calls } = recordingChannel();
    const handle = jest.fn(() => Promise.reject(new Error('SMTP down')));
    const sleeps: number[] = [];

    const result = await handleDelivery(msg, {
      channel,
      queue: 'q',
      consumer: new QueueConsumer('notification.test', handle),
      logger: silent,
      sleep: (ms) => {
        sleeps.push(ms);
        return Promise.resolve();
      },
    });

    expect(result).toBe('DEAD_LETTERED');
    expect(handle).toHaveBeenCalledTimes(3);
    expect(sleeps).toEqual([1000, 2000]);
    expect(calls).toEqual({ acks: 0, nacks: [false] });
  });

  it('nacks a NonRetryableError at once, without retrying', async () => {
    const { channel, calls } = recordingChannel();
    const handle = jest.fn(() =>
      Promise.reject(new NonRetryableError('Malformed payload')),
    );
    const sleep = jest.fn(() => Promise.resolve());

    const result = await handleDelivery(msg, {
      channel,
      queue: 'q',
      consumer: new QueueConsumer('notification.test', handle),
      logger: silent,
      sleep,
    });

    expect(result).toBe('DEAD_LETTERED');
    expect(handle).toHaveBeenCalledTimes(1);
    expect(sleep).not.toHaveBeenCalled();
    expect(calls).toEqual({ acks: 0, nacks: [false] });
  });

  it('acks after a transient error that succeeds on retry', async () => {
    const { channel, calls } = recordingChannel();
    const handle = jest
      .fn<Promise<void>, [Buffer]>()
      .mockRejectedValueOnce(new Error('deadlock'))
      .mockResolvedValueOnce(undefined);

    const result = await handleDelivery(msg, {
      channel,
      queue: 'q',
      consumer: new QueueConsumer('notification.test', handle),
      logger: silent,
      sleep: () => Promise.resolve(),
    });

    expect(result).toBe('ACK_PROCESSED');
    expect(calls).toEqual({ acks: 1, nacks: [] });
  });
});
