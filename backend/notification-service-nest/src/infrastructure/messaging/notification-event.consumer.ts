import {
  Inject,
  Injectable,
  Logger,
  Optional,
  type BeforeApplicationShutdown,
  type OnModuleInit,
} from '@nestjs/common';
import {
  NonRetryableError,
  consumeEvents,
  type AmqpChannelLike,
  type AmqpMessageLike,
  type DeliveryOutcome,
  type MessageConsumer,
} from '@agricontract/eventing';
import type { Channel, ConsumeMessage } from 'amqplib';
import {
  ClaimWaitAbortedError,
  NotificationError,
  failureCode,
} from '../../application/notification-errors';
import { eventingLogger } from '../logging/eventing-logger';
import { AmqpService } from './amqp.provider';
import { LEGACY_ROUTES, type LegacyRoutes } from './legacy-routes';
import { TOPOLOGY, queueName } from './topology';

/**
 * Upper bound on waiting for in-flight messages at shutdown: above the
 * longest SMTP attempt (30s socket timeout), so a send in progress can finish
 * and be recorded, and below the compose `stop_grace_period` (60s), so the
 * channel and pool still close before SIGKILL. Messages still running then
 * are not acked: closing the channel makes the broker redeliver them, where
 * SENT recipients are skipped and the dead claim is taken over after its lease.
 */
export const DRAIN_TIMEOUT_MS = 40_000;

/**
 * Aborted by the consumer when shutdown begins; its signal ends the claim
 * wait of NotificationSender, so a waiting message is parked, not drained.
 */
export const SHUTDOWN_CONTROLLER = Symbol('SHUTDOWN_CONTROLLER');

/** Asked at the start of every attempt whether it may run. */
export interface AttemptGate {
  /** A promise that never settles when the attempt must not start; undefined to run it. */
  hold(body: Buffer): Promise<never> | undefined;
}

/** One queue: parse the flat payload, run the handler, report PROCESSED (no envelope, D6). */
export class QueueConsumer implements MessageConsumer {
  constructor(
    readonly consumerName: string,
    private readonly handle: (body: Buffer) => Promise<void>,
    private readonly gate?: AttemptGate,
  ) {}

  /**
   * Called by handleDelivery once per attempt. An attempt the gate holds
   * never starts: no handler, no database claim, and handleDelivery neither
   * acks nor nacks, so the message stays unacked.
   *
   * handleDelivery logs err.message on every retry and dead-letter, so only
   * errors with vetted messages pass through: the parsers' NonRetryableError
   * (field names and types) and NotificationError (event id and code). Any
   * other error is replaced by its code, keeping it retryable.
   *
   * A claim wait ended by shutdown is parked like a retry: the delivery
   * holding the claim may still fail, so this message must come back.
   */
  async handleMessage(body: Buffer): Promise<DeliveryOutcome> {
    const held = this.gate?.hold(body);
    if (held) return held;
    try {
      await this.handle(body);
    } catch (err) {
      if (err instanceof ClaimWaitAbortedError) {
        const parked = this.gate?.hold(body);
        if (parked) return parked;
      }
      if (
        err instanceof NonRetryableError ||
        err instanceof NotificationError
      ) {
        throw err;
      }
      throw new Error(`Unexpected failure (${failureCode(err)})`);
    }
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
  /** Contents of in-flight messages whose next attempt was held at shutdown. */
  private readonly parked = new Set<Buffer>();
  private idleWaiters: Array<() => void> = [];
  private channel?: Channel;
  private consumerTags: string[] = [];
  private stopping = false;
  /**
   * Once shutdown has begun, no attempt starts: a retry after its backoff (or
   * a delivery that arrived just before the cancel) is parked, unacked, and
   * the broker redelivers it when the channel closes. Java stops the same
   * way. Retrying here instead could dead-letter it during the drain while
   * SMTP is down (parity report, scenario 4).
   */
  private readonly gate: AttemptGate = {
    hold: (body) => {
      if (!this.stopping) return undefined;
      this.parked.add(body);
      this.wakeIfIdle();
      return new Promise<never>(() => undefined);
    },
  };

  constructor(
    private readonly amqp: AmqpService,
    @Inject(LEGACY_ROUTES) private readonly routes: LegacyRoutes,
    @Optional()
    @Inject(SHUTDOWN_CONTROLLER)
    private readonly shutdown?: AbortController,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.amqp.onChannelReady((channel) => this.attach(channel));
  }

  /** Number of delivered messages not yet acked or nacked, parked ones included. */
  get inFlightCount(): number {
    return this.inFlight.size;
  }

  /** Number of in-flight messages parked at shutdown (left unacked for redelivery). */
  get parkedCount(): number {
    return this.parked.size;
  }

  /** In-flight messages the drain waits for: running an attempt or in its backoff, not parked. */
  private get busyCount(): number {
    let busy = 0;
    for (const msg of this.inFlight) if (!this.parked.has(msg.content)) busy++;
    return busy;
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
          consumer: new QueueConsumer(
            `notification.${key}`,
            this.routes[key],
            this.gate,
          ),
          logger,
        });
        this.consumerTags.push(tag);
      }
    }
    this.logger.log(`Consuming ${this.consumerTags.length} queues`);
  }

  /**
   * Shutdown, step 2 (after AmqpService stopped reconnecting, before it closes
   * the channel and MysqlService the pool): stop starting attempts (gate),
   * cancel every consumer, then wait until every message already delivered is
   * acked, nacked or parked. An attempt running now finishes; a message in
   * its retry backoff is parked when the backoff ends, one waiting for
   * another delivery's claim at once (SHUTDOWN_CONTROLLER).
   */
  async beforeApplicationShutdown(): Promise<void> {
    this.stopping = true;
    this.shutdown?.abort();
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
    if (this.parked.size > 0) {
      this.logger.log(
        `${this.parked.size} message(s) not retried during shutdown; they stay unacked and will be redelivered`,
      );
    }
  }

  private waitUntilIdle(timeoutMs: number): Promise<void> {
    if (this.busyCount === 0) return Promise.resolve();
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        this.logger.warn(
          `${this.busyCount} message(s) still in flight after ${timeoutMs}ms; they stay unacked and will be redelivered`,
        );
        resolve();
      }, timeoutMs);
      this.idleWaiters.push(() => {
        clearTimeout(timer);
        resolve();
      });
    });
  }

  /** Wakes the drain once every in-flight message is settled or parked. */
  private wakeIfIdle(): void {
    if (this.busyCount > 0) return;
    const waiters = this.idleWaiters;
    this.idleWaiters = [];
    waiters.forEach((wake) => wake());
  }

  /**
   * Wraps the channel handed to consumeEvents to know which deliveries are
   * not settled yet; ack/nack still go straight to amqplib.
   */
  private track(channel: Channel): AmqpChannelLike {
    const settled = (msg: AmqpMessageLike) => {
      this.inFlight.delete(msg);
      this.wakeIfIdle();
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
