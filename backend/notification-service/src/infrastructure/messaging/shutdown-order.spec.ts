import { EventEmitter } from 'node:events';
import { Logger } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import type { AmqpMessageLike } from '@agricontract/eventing';
import { validate } from '../../config/env.validation';
import { MYSQL_POOL, MysqlService } from '../persistence/mysql.provider';
import { AMQP_CONNECT, AmqpService } from './amqp.provider';
import { LEGACY_ROUTES, type LegacyRoutes } from './legacy-routes';
import {
  DRAIN_TIMEOUT_MS,
  NotificationEventConsumer,
} from './notification-event.consumer';
import { TOPOLOGY } from './topology';

type OnMessage = (msg: AmqpMessageLike | null) => void;

/** amqplib channel stand-in with what AmqpService and the consumer call; records the order of events. */
class FakeChannel extends EventEmitter {
  readonly consumers = new Map<string, OnMessage>();
  constructor(private readonly events: string[]) {
    super();
  }
  prefetch = jest.fn(() => Promise.resolve());
  assertExchange = jest.fn(() => Promise.resolve({}));
  assertQueue = jest.fn(() => Promise.resolve({}));
  bindQueue = jest.fn(() => Promise.resolve({}));
  consume = jest.fn((queue: string, onMessage: OnMessage) => {
    this.consumers.set(queue, onMessage);
    return Promise.resolve({ consumerTag: `tag:${queue}` });
  });
  cancel = jest.fn(() => Promise.resolve({}));
  ack = jest.fn(() => void this.events.push('ack'));
  nack = jest.fn(() => void this.events.push('nack'));
  close = jest.fn(() => {
    this.events.push('channel.close');
    return Promise.resolve();
  });
}

class FakeConnection extends EventEmitter {
  constructor(readonly channel: FakeChannel) {
    super();
  }
  createChannel = jest.fn(() => Promise.resolve(this.channel));
  close = jest.fn(() => Promise.resolve());
}

/**
 * The real Nest lifecycle (moduleRef.close() runs the same hooks as SIGTERM
 * with enableShutdownHooks) over AmqpService, the consumer and MysqlService.
 */
async function startApp(handler: () => Promise<void>) {
  const events: string[] = [];
  const channel = new FakeChannel(events);
  const connection = new FakeConnection(channel);
  const pool = {
    query: jest.fn(() => Promise.resolve([[], []])),
    end: jest.fn(() => {
      events.push('pool.end');
      return Promise.resolve();
    }),
  };
  const routes = Object.fromEntries(
    TOPOLOGY.flatMap((row) => row.routingKeys).map((key) => [key, handler]),
  ) as unknown as LegacyRoutes;

  const moduleRef = await Test.createTestingModule({
    imports: [
      ConfigModule.forRoot({
        isGlobal: true,
        ignoreEnvFile: true,
        validate: () => validate({}),
      }),
    ],
    providers: [
      AmqpService,
      { provide: AMQP_CONNECT, useValue: () => Promise.resolve(connection) },
      NotificationEventConsumer,
      { provide: LEGACY_ROUTES, useValue: routes },
      MysqlService,
      { provide: MYSQL_POOL, useValue: pool },
    ],
  }).compile();
  await moduleRef.init();
  await jest.advanceTimersByTimeAsync(0); // AmqpService connects in the background

  const deliver = () => {
    const onMessage = channel.consumers.get('notification-svc.contract.signed');
    if (!onMessage) throw new Error('consumer not attached');
    onMessage({ content: Buffer.from('{}'), fields: {} });
  };
  let closed = false;
  const close = () => {
    const done = moduleRef.close().then(() => (closed = true));
    return { done, closed: () => closed };
  };
  return { events, deliver, close };
}

describe('shutdown order (Nest lifecycle)', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    for (const level of ['log', 'warn', 'error', 'debug'] as const) {
      jest.spyOn(Logger.prototype, level).mockImplementation(() => undefined);
    }
  });
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('acks the in-flight message, then closes the channel, then ends the pool', async () => {
    let finish!: () => void;
    const app = await startApp(
      () => new Promise<void>((resolve) => (finish = resolve)),
    );
    app.deliver();
    await jest.advanceTimersByTimeAsync(0);

    const shutdown = app.close();
    await jest.advanceTimersByTimeAsync(30_000); // a send that takes the full SMTP socket timeout
    expect(shutdown.closed()).toBe(false);
    expect(app.events).toEqual([]);

    finish();
    await jest.advanceTimersByTimeAsync(0);
    await shutdown.done;

    expect(app.events).toEqual(['ack', 'channel.close', 'pool.end']);
  });

  it('after the drain timeout closes the channel without ack or nack, and only then ends the pool', async () => {
    const app = await startApp(() => new Promise<void>(() => undefined));
    app.deliver();
    await jest.advanceTimersByTimeAsync(0);

    const shutdown = app.close();
    await jest.advanceTimersByTimeAsync(DRAIN_TIMEOUT_MS - 1);
    expect(shutdown.closed()).toBe(false);
    expect(app.events).toEqual([]); // pool still open for the running handler

    await jest.advanceTimersByTimeAsync(1);
    await shutdown.done;

    expect(app.events).toEqual(['channel.close', 'pool.end']);
  });

  it('a retry due during shutdown is parked: the channel closes after its backoff, without ack or nack', async () => {
    let calls = 0;
    const app = await startApp(() => {
      calls += 1;
      return Promise.reject(new Error('SMTP down'));
    });
    app.deliver();
    await jest.advanceTimersByTimeAsync(0); // attempt 1 failed, 1s backoff running

    const shutdown = app.close();
    await jest.advanceTimersByTimeAsync(1_000);
    await shutdown.done;

    expect(calls).toBe(1); // attempt 2 never ran
    expect(app.events).toEqual(['channel.close', 'pool.end']);
  });
});
