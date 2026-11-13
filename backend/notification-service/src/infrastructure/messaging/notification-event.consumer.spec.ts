import { Logger } from '@nestjs/common';
import {
  NonRetryableError,
  handleDelivery,
  type AmqpChannelLike,
  type AmqpMessageLike,
} from '@agricontract/eventing';
import type { Channel } from 'amqplib';
import { ClaimWaitAbortedError } from '../../application/notification-errors';
import {
  anchorCommand,
  anchorEnvelope,
  cancelCommand,
  activationFailedCommand,
  activationFailedEnvelope,
  cancelEnvelope,
  kycCommand,
  kycEnvelope,
  lockCommand,
  lockEnvelope,
  terminatedCommand,
  terminatedEnvelope,
  terminatedPayload,
} from '../../../test/support/commands';
import { commandRoutes, type CommandUseCases } from './command-routes';
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
  'contractAnchor',
  'activationCancelRequested',
  'contractActivationFailed',
  'contractTerminated',
  'userKycResult',
  'userLockChanged',
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
  // Fake use cases: execute() is the mock, routed through the real route tables.
  const useCases = Object.fromEntries(
    HANDLERS.map((h) => [h, { execute: service[h] }]),
  ) as unknown as NotificationUseCases & CommandUseCases;
  const shutdownController = new AbortController();
  const consumer = new NotificationEventConsumer(
    amqp,
    legacyRoutes(useCases),
    commandRoutes(useCases),
    shutdownController,
  );
  const open = async (channel: FakeChannel) => {
    for (const cb of readyCallbacks) await cb(channel as unknown as Channel);
  };
  return { consumer, service, open, shutdownSignal: shutdownController.signal };
}

/** Lets the delivery promises chained by consumeEvents run. */
const flush = async () => {
  for (let i = 0; i < 10; i += 1) await new Promise((r) => setImmediate(r));
};

const PAYLOADS: Record<
  string,
  { handler: (typeof HANDLERS)[number]; payload: Record<string, unknown> }
> = {
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

  it('consumes every queue on every channel, again after a reconnect', async () => {
    const { consumer, open } = setup();
    await consumer.onModuleInit();
    const first = new FakeChannel();
    const second = new FakeChannel();

    await open(first);
    await open(second);

    for (const channel of [first, second]) {
      expect(channel.consume).toHaveBeenCalledTimes(queueNames().length);
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

  /*
   * The Phase 2 commands are not in PAYLOADS: that table delivers every event
   * with `eventType: 'ignored'`, because a flat Phase 1 payload has no
   * envelope and its parser reads only the fields the template needs. A
   * command does have an envelope, and its eventType is part of the contract,
   * so the two cases below say what this queue does with it instead.
   */
  const ANCHOR_QUEUE =
    'notification-svc.notification.contract_anchor_requested';

  it('runs the anchor use case with the parsed command, recipients included, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(ANCHOR_QUEUE, anchorEnvelope());
    await flush();

    expect(service.contractAnchor).toHaveBeenCalledTimes(1);
    expect(service.contractAnchor.mock.calls[0][0]).toEqual(anchorCommand());
    for (const other of HANDLERS.filter((h) => h !== 'contractAnchor')) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it('runs the activation-cancel use case on its own queue, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(
      'notification-svc.notification.activation_cancel_requested',
      cancelEnvelope(),
    );
    await flush();

    expect(service.activationCancelRequested).toHaveBeenCalledTimes(1);
    expect(service.activationCancelRequested.mock.calls[0][0]).toEqual(
      cancelCommand(),
    );
    for (const other of HANDLERS.filter(
      (h) => h !== 'activationCancelRequested',
    )) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it('dead-letters an anchor command delivered to the activation-cancel queue', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    channel.deliver(
      'notification-svc.notification.activation_cancel_requested',
      anchorEnvelope(),
    );
    await flush();

    expect(service.activationCancelRequested).not.toHaveBeenCalled();
    expect(service.contractAnchor).not.toHaveBeenCalled();
    expect(channel.nacked).toHaveLength(1);
  });

  it('runs the activation-failed use case on its own queue, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(
      'notification-svc.notification.contract_activation_failed_requested',
      activationFailedEnvelope(),
    );
    await flush();

    expect(service.contractActivationFailed).toHaveBeenCalledTimes(1);
    expect(service.contractActivationFailed.mock.calls[0][0]).toEqual(
      activationFailedCommand(),
    );
    for (const other of HANDLERS.filter(
      (h) => h !== 'contractActivationFailed',
    )) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it('dead-letters an activation-cancel command delivered to the activation-failed queue', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    channel.deliver(
      'notification-svc.notification.contract_activation_failed_requested',
      cancelEnvelope(),
    );
    await flush();

    expect(service.contractActivationFailed).not.toHaveBeenCalled();
    expect(service.activationCancelRequested).not.toHaveBeenCalled();
    expect(channel.nacked).toHaveLength(1);
  });

  it('runs the contract-terminated use case for a withdrawn offer, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(
      'notification-svc.notification.contract_terminated_requested',
      terminatedEnvelope(),
    );
    await flush();

    expect(service.contractTerminated).toHaveBeenCalledTimes(1);
    expect(service.contractTerminated.mock.calls[0][0]).toEqual(
      terminatedCommand(),
    );
    for (const other of HANDLERS.filter((h) => h !== 'contractTerminated')) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it.each([
    [
      'a termination type without a template yet',
      { terminationType: 'TERMINATION_FOR_BREACH' },
    ],
    ['a withdrawal requested by SYSTEM', { requestedBy: 'SYSTEM' }],
  ])(
    'dead-letters %s at once, unsent, instead of acking it',
    async (_case, patch) => {
      const { consumer, service, open } = setup();
      await consumer.onModuleInit();
      const channel = new FakeChannel();
      await open(channel);

      const msg = channel.deliver(
        'notification-svc.notification.contract_terminated_requested',
        terminatedEnvelope({ payload: terminatedPayload(patch) }),
      );
      await flush();

      expect(service.contractTerminated).not.toHaveBeenCalled();
      expect(channel.nacked).toEqual([{ msg, requeue: false }]);
      expect(channel.acked).toEqual([]);
    },
  );

  it('runs the KYC-result use case on its own queue, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(
      'notification-svc.notification.user_kyc_result_requested',
      kycEnvelope(),
    );
    await flush();

    expect(service.userKycResult).toHaveBeenCalledTimes(1);
    expect(service.userKycResult.mock.calls[0][0]).toEqual(kycCommand());
    for (const other of HANDLERS.filter((h) => h !== 'userKycResult')) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it('runs the lock-changed use case on its own queue, and acks', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver(
      'notification-svc.notification.user_lock_changed_requested',
      lockEnvelope(),
    );
    await flush();

    expect(service.userLockChanged).toHaveBeenCalledTimes(1);
    expect(service.userLockChanged.mock.calls[0][0]).toEqual(lockCommand());
    for (const other of HANDLERS.filter((h) => h !== 'userLockChanged')) {
      expect(service[other]).not.toHaveBeenCalled();
    }
    expect(channel.acked).toEqual([msg]);
    expect(channel.nacked).toEqual([]);
  });

  it('dead-letters a KYC-result command delivered to the lock-changed queue', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    channel.deliver(
      'notification-svc.notification.user_lock_changed_requested',
      kycEnvelope(),
    );
    await flush();

    expect(service.userLockChanged).not.toHaveBeenCalled();
    expect(service.userKycResult).not.toHaveBeenCalled();
    expect(channel.nacked).toHaveLength(1);
  });

  it('dead-letters a contract command delivered to the KYC-result queue', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    channel.deliver(
      'notification-svc.notification.user_kyc_result_requested',
      cancelEnvelope(),
    );
    await flush();

    expect(service.userKycResult).not.toHaveBeenCalled();
    expect(service.activationCancelRequested).not.toHaveBeenCalled();
    expect(channel.nacked).toHaveLength(1);
  });

  it('dead-letters a command whose eventType is not this queue’s, without calling the handler', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    channel.deliver(
      ANCHOR_QUEUE,
      anchorEnvelope({
        eventType: 'notification.contract_terminated_requested',
      }),
    );
    await flush();

    expect(service.contractAnchor).not.toHaveBeenCalled();
    expect(channel.nacked).toHaveLength(1);
    expect(channel.acked).toEqual([]);
  });

  it('dead-letters a malformed payload at once, without calling the handler', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);

    const msg = channel.deliver('notification-svc.category.rejected', '{oops');
    await flush();

    expect(service.categoryRejected).not.toHaveBeenCalled();
    expect(channel.nacked).toEqual([{ msg, requeue: false }]);
    expect(channel.acked).toEqual([]);
  });

  it('on shutdown cancels every consumer, then waits for in-flight messages', async () => {
    const { consumer, service, open } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);
    let finish!: () => void;
    service.categoryApproved.mockImplementation(
      () => new Promise<void>((resolve) => (finish = resolve)),
    );

    const msg = channel.deliver(
      'notification-svc.category.approved',
      PAYLOADS['notification-svc.category.approved'].payload,
    );
    await flush();
    expect(consumer.inFlightCount).toBe(1);

    let drained = false;
    const shutdown = consumer
      .beforeApplicationShutdown()
      .then(() => (drained = true));
    await flush();

    expect(channel.cancel).toHaveBeenCalledTimes(queueNames().length);
    expect(channel.cancel).toHaveBeenCalledWith(
      'tag:notification-svc.category.approved',
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

    /** One category.approved message in flight whose handler runs until `finish()`. */
    async function oneInFlight() {
      const { consumer, service, open } = setup();
      await consumer.onModuleInit();
      const channel = new FakeChannel();
      await open(channel);
      let finish!: () => void;
      service.categoryApproved.mockImplementation(
        () => new Promise<void>((resolve) => (finish = resolve)),
      );
      const msg = channel.deliver(
        'notification-svc.category.approved',
        PAYLOADS['notification-svc.category.approved'].payload,
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

describe('NotificationEventConsumer at shutdown: no new attempt starts (fake timers)', () => {
  const QUEUE = 'notification-svc.category.rejected';
  const PAYLOAD = PAYLOADS[QUEUE].payload;
  let log: jest.SpyInstance;

  beforeEach(() => {
    jest.useFakeTimers();
    for (const level of ['warn', 'error', 'debug'] as const) {
      jest.spyOn(Logger.prototype, level).mockImplementation(() => undefined);
    }
    log = jest
      .spyOn(Logger.prototype, 'log')
      .mockImplementation(() => undefined);
  });
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  async function started() {
    const { consumer, service, open, shutdownSignal } = setup();
    await consumer.onModuleInit();
    const channel = new FakeChannel();
    await open(channel);
    let drained = false;
    const shutdown = () => {
      const done = consumer
        .beforeApplicationShutdown()
        .then(() => (drained = true));
      return done;
    };
    return {
      consumer,
      channel,
      handler: service.categoryRejected,
      shutdown,
      shutdownSignal,
      drained: () => drained,
    };
  }

  it('an attempt running when shutdown begins finishes and is acked', async () => {
    const { consumer, channel, handler, shutdown, drained } = await started();
    let finish!: () => void;
    handler.mockImplementationOnce(
      () => new Promise<void>((resolve) => (finish = resolve)),
    );
    const msg = channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(0);

    const done = shutdown();
    await jest.advanceTimersByTimeAsync(5_000);
    expect(drained()).toBe(false);
    finish();
    await jest.advanceTimersByTimeAsync(0);
    await done;

    expect(handler).toHaveBeenCalledTimes(1);
    expect(channel.acked).toEqual([msg]);
    expect(consumer.parkedCount).toBe(0);
  });

  it('an attempt running when shutdown begins that fails for good is nacked to the DLQ as usual', async () => {
    const { channel, handler, shutdown } = await started();
    let fail!: (err: Error) => void;
    handler.mockImplementationOnce(
      () => new Promise<void>((_resolve, reject) => (fail = reject)),
    );
    const msg = channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(0);

    const done = shutdown();
    fail(new NonRetryableError('bad payload'));
    await jest.advanceTimersByTimeAsync(0);
    await done;

    expect(channel.nacked).toEqual([{ msg, requeue: false }]);
  });

  it('the retry after a failed attempt is parked: handler not called again, no ack, no nack, drain does not wait for it', async () => {
    const { consumer, channel, handler, shutdown, drained } = await started();
    let fail!: (err: Error) => void;
    handler.mockImplementationOnce(
      () => new Promise<void>((_resolve, reject) => (fail = reject)),
    );
    channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(0);

    const done = shutdown();
    fail(new Error('SMTP down')); // retryable: handleDelivery sleeps 1s, then tries again
    await jest.advanceTimersByTimeAsync(999);
    expect(drained()).toBe(false); // still in its backoff
    await jest.advanceTimersByTimeAsync(1);
    await done;

    expect(drained()).toBe(true); // after 1s, not the 40s drain timeout
    expect(handler).toHaveBeenCalledTimes(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
    expect(consumer.inFlightCount).toBe(1);
    expect(consumer.parkedCount).toBe(1);
    expect(log).toHaveBeenCalledWith(
      '1 message(s) not retried during shutdown; they stay unacked and will be redelivered',
    );

    // Nothing happens later either: no further attempt, never settled.
    await jest.advanceTimersByTimeAsync(10 * DRAIN_TIMEOUT_MS);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
  });

  it('a message already in its backoff when shutdown begins is parked when the backoff ends', async () => {
    const { consumer, channel, handler, shutdown } = await started();
    handler.mockRejectedValueOnce(new Error('SMTP down'));
    channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(500); // attempt 1 failed, 500ms into the 1s backoff

    const done = shutdown();
    await jest.advanceTimersByTimeAsync(500);
    await done;

    expect(handler).toHaveBeenCalledTimes(1);
    expect(consumer.parkedCount).toBe(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
  });

  it('a delivery reaching the consumer after shutdown began is parked without running', async () => {
    const { consumer, channel, handler, shutdown } = await started();
    const done = shutdown(); // consumers are being cancelled

    channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(0);
    await done;

    expect(handler).not.toHaveBeenCalled();
    expect(consumer.parkedCount).toBe(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
  });

  it("a message waiting for another delivery's claim is parked at once: the wait ends, no ack, no nack", async () => {
    const { consumer, channel, handler, shutdown, shutdownSignal, drained } =
      await started();
    // NotificationSender's claim wait: ends with ClaimWaitAbortedError when the signal aborts.
    handler.mockImplementationOnce(
      () =>
        new Promise<void>((_resolve, reject) =>
          shutdownSignal.addEventListener('abort', () =>
            reject(new ClaimWaitAbortedError('e1')),
          ),
        ),
    );
    channel.deliver(QUEUE, PAYLOAD);
    await jest.advanceTimersByTimeAsync(0);

    const done = shutdown();
    await jest.advanceTimersByTimeAsync(0);
    await done;

    expect(shutdownSignal.aborted).toBe(true);
    expect(drained()).toBe(true); // at once, not after the 40s drain timeout
    expect(consumer.parkedCount).toBe(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);

    await jest.advanceTimersByTimeAsync(10 * DRAIN_TIMEOUT_MS);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
  });

  it('waits for a running send next to a parked message, and on timeout leaves both unacked', async () => {
    const { consumer, channel, handler, shutdown, drained } = await started();
    handler
      .mockRejectedValueOnce(new Error('SMTP down')) // message 1: will be parked
      .mockImplementationOnce(() => new Promise<void>(() => undefined)); // message 2: send hangs
    channel.deliver(QUEUE, { ...PAYLOAD, eventId: 'e-park' });
    channel.deliver(QUEUE, { ...PAYLOAD, eventId: 'e-hang' });
    await jest.advanceTimersByTimeAsync(0);

    const done = shutdown();
    await jest.advanceTimersByTimeAsync(1_000);
    expect(consumer.parkedCount).toBe(1);
    expect(drained()).toBe(false); // message 2 is still sending

    await jest.advanceTimersByTimeAsync(DRAIN_TIMEOUT_MS - 1_000);
    await done;

    expect(consumer.inFlightCount).toBe(2);
    expect(channel.acked).toEqual([]);
    expect(channel.nacked).toEqual([]);
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
