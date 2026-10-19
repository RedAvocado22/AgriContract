import {
  Inject,
  Injectable,
  Logger,
  type BeforeApplicationShutdown,
  type OnModuleInit,
} from '@nestjs/common';
import {
  consumeEvents,
  type AmqpChannelLike,
  type AmqpMessageLike,
  type DeliveryOutcome,
  type MessageConsumer,
} from '@agricontract/eventing';
import type { Channel, ConsumeMessage } from 'amqplib';
import { eventingLogger } from '../logging/eventing-logger';
import { AmqpService } from './amqp.provider';
import { LEGACY_ROUTES, type LegacyRoutes } from './legacy-routes';
import { TOPOLOGY, queueName } from './topology';

/**
 * Upper bound on waiting for in-flight messages at shutdown. Docker sends
 * SIGKILL 10s after SIGTERM by default; anything still running then stays
 * unacked and is redelivered, where SENT recipients are skipped.
 */
export const DRAIN_TIMEOUT_MS = 8_000;

/** One queue: parse the flat payload, run the handler, report PROCESSED (no envelope, D6). */
export class QueueConsumer implements MessageConsumer {
  constructor(
    readonly consumerName: string,
    private readonly handle: (body: Buffer) => Promise<void>,
  ) {}

  async handleMessage(body: Buffer): Promise<DeliveryOutcome> {
    await this.handle(body);
    return { status: 'PROCESSED' };
  }
}

/**
 * Attaches one consumer per notification queue on every channel AmqpService
 * opens (after each reconnect too: consumers die with their channel). Ack,
 * retry (3 attempts, 1s/2s backoff) and dead-lettering are eventing's
 * handleDelivery, through consumeEvents.
 */
@Injectable()
export class NotificationEventConsumer
  implements OnModuleInit, BeforeApplicationShutdown
{
  private readonly logger = new Logger(NotificationEventConsumer.name);
  private readonly inFlight = new Set<AmqpMessageLike>();
  private idleWaiters: Array<() => void> = [];
  private channel?: Channel;
  private consumerTags: string[] = [];
  private stopping = false;

  constructor(
    private readonly amqp: AmqpService,
    @Inject(LEGACY_ROUTES) private readonly routes: LegacyRoutes,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.amqp.onChannelReady((channel) => this.attach(channel));
  }

  /** Number of delivered messages not yet acked or nacked. */
  get inFlightCount(): number {
    return this.inFlight.size;
  }

  private async attach(channel: Channel): Promise<void> {
    if (this.stopping) return;
    this.channel = channel;
    this.consumerTags = [];
    const tracked = this.track(channel);
    const logger = eventingLogger(this.logger);
    for (const row of TOPOLOGY) {
      for (const key of row.routingKeys) {
        const tag = await consumeEvents({
          channel: tracked,
          queue: queueName(key),
          consumer: new QueueConsumer(`notification.${key}`, this.routes[key]),
          logger,
        });
        this.consumerTags.push(tag);
      }
    }
    this.logger.log(`Consuming ${this.consumerTags.length} queues`);
  }

  /**
   * Shutdown, step 2 (after AmqpService stopped reconnecting, before it closes
   * the channel and MysqlService the pool): cancel every consumer, then wait
   * until the messages already delivered are acked or nacked.
   */
  async beforeApplicationShutdown(): Promise<void> {
    this.stopping = true;
    const channel = this.channel;
    const tags = this.consumerTags;
    this.consumerTags = [];
    for (const tag of tags) {
      try {
        await channel?.cancel(tag);
      } catch (err) {
        this.logger.debug(
          `Cancel of consumer ${tag} failed (channel gone?): ${(err as Error).message}`,
        );
      }
    }
    if (tags.length > 0) {
      this.logger.log(
        `Cancelled ${tags.length} consumers; waiting for ${this.inFlight.size} in-flight message(s)`,
      );
    }
    await this.waitUntilIdle(DRAIN_TIMEOUT_MS);
  }

  private waitUntilIdle(timeoutMs: number): Promise<void> {
    if (this.inFlight.size === 0) return Promise.resolve();
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        this.logger.warn(
          `${this.inFlight.size} message(s) still in flight after ${timeoutMs}ms; they stay unacked and will be redelivered`,
        );
        resolve();
      }, timeoutMs);
      this.idleWaiters.push(() => {
        clearTimeout(timer);
        resolve();
      });
    });
  }

  /**
   * Wraps the channel handed to consumeEvents to know which deliveries are
   * not settled yet; ack/nack still go straight to amqplib.
   */
  private track(channel: Channel): AmqpChannelLike {
    const settled = (msg: AmqpMessageLike) => {
      this.inFlight.delete(msg);
      if (this.inFlight.size === 0) {
        const waiters = this.idleWaiters;
        this.idleWaiters = [];
        waiters.forEach((wake) => wake());
      }
    };
    return {
      consume: (queue, onMessage, options) =>
        channel.consume(
          queue,
          (msg) => {
            if (msg) this.inFlight.add(msg);
            onMessage(msg);
          },
          options,
        ),
      ack: (msg) => {
        try {
          channel.ack(msg as ConsumeMessage);
        } finally {
          settled(msg);
        }
      },
      nack: (msg, allUpTo, requeue) => {
        try {
          channel.nack(msg as ConsumeMessage, allUpTo, requeue);
        } finally {
          settled(msg);
        }
      },
    };
  }
}
