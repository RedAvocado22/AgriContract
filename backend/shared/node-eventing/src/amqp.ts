import type { HandleOutcome } from './dedupe.js';
import { isNonRetryable } from './errors.js';
import { consoleLogger, type EventPublisher, type Logger, type PublishMessage } from './ports.js';

/**
 * RabbitMQ adapters. Typed structurally against the subset of `amqplib` we use, so
 * this package has no hard runtime dependency: services pass in their own channel
 * (`await connection.createConfirmChannel()` / `createChannel()`).
 */

export interface AmqpPublishOptions {
  persistent?: boolean;
  contentType?: string;
  contentEncoding?: string;
  messageId?: string;
  type?: string;
  timestamp?: number;
  headers?: Record<string, unknown>;
}

/** Subset of amqplib `ConfirmChannel`. */
export interface AmqpConfirmChannelLike {
  publish(
    exchange: string,
    routingKey: string,
    content: Buffer,
    options: AmqpPublishOptions,
    callback: (err: unknown, ok?: unknown) => void,
  ): boolean;
}

export interface AmqpMessageLike {
  content: Buffer;
  properties?: { messageId?: unknown; type?: unknown };
  fields?: { routingKey?: string; redelivered?: boolean };
}

/** Subset of amqplib `Channel`. */
export interface AmqpChannelLike {
  consume(
    queue: string,
    onMessage: (msg: AmqpMessageLike | null) => void,
    options?: { noAck?: boolean },
  ): Promise<{ consumerTag: string }>;
  ack(message: AmqpMessageLike): void;
  nack(message: AmqpMessageLike, allUpTo?: boolean, requeue?: boolean): void;
  cancel?(consumerTag: string): Promise<unknown>;
}

/** Publisher using RabbitMQ publisher confirms: resolves only when the broker acked. */
export class AmqpConfirmPublisher implements EventPublisher {
  constructor(private readonly channel: AmqpConfirmChannelLike) {}

  publish(message: PublishMessage): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.channel.publish(
          message.exchange,
          message.routingKey,
          message.body,
          {
            persistent: true,
            contentType: 'application/json',
            contentEncoding: 'utf-8',
            messageId: message.messageId,
            type: message.type,
            timestamp: message.timestamp,
            headers: message.headers,
          },
          (err) => (err ? reject(err instanceof Error ? err : new Error(String(err))) : resolve()),
        );
      } catch (err) {
        reject(err);
      }
    });
  }
}

export interface MessageConsumer {
  readonly consumerName: string;
  handleMessage(body: Buffer): Promise<HandleOutcome>;
}

export interface ConsumeOptions {
  channel: AmqpChannelLike;
  queue: string;
  /** Usually an IdempotentConsumer. */
  consumer: MessageConsumer;
  /** Total attempts for transient errors before dead-lettering (Java: 3). */
  maxAttempts?: number;
  /** Backoff between attempts, ms (Java: 1000, x2, max 4000). */
  initialBackoffMs?: number;
  backoffMultiplier?: number;
  maxBackoffMs?: number;
  logger?: Logger;
  sleep?: (ms: number) => Promise<void>;
}

/**
 * - ACK_PROCESSED / ACK_DUPLICATE: handled (or already handled) and acked.
 * - DEAD_LETTERED: nacked without requeue, so the queue's DLX routes it to the DLQ.
 * - UNSETTLED: the channel refused the ack/nack (e.g. it closed meanwhile). The message stays unacked;
 *   the broker redelivers it on a new channel and consumer dedupe drops the repeat.
 */
export type DeliveryResult = 'ACK_PROCESSED' | 'ACK_DUPLICATE' | 'DEAD_LETTERED' | 'UNSETTLED';

/**
 * Handles one delivery: ack on success or duplicate; nack(requeue=false) so the queue's
 * dead-letter exchange routes it to the DLQ when the envelope is invalid, the handler
 * throws NonRetryableError, or transient errors exhaust `maxAttempts`.
 * The queue must be declared with `x-dead-letter-exchange` (same as the Java services).
 *
 * Never rejects because of the channel: amqplib throws from ack/nack once the channel is closed, and
 * that must neither crash the service (unhandled rejection) nor be mistaken for a handler failure.
 */
export async function handleDelivery(msg: AmqpMessageLike, options: ConsumeOptions): Promise<DeliveryResult> {
  const logger = options.logger ?? consoleLogger;
  const maxAttempts = Math.max(1, options.maxAttempts ?? 3);
  const multiplier = options.backoffMultiplier ?? 2;
  const maxBackoff = options.maxBackoffMs ?? 4000;
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  let backoff = options.initialBackoffMs ?? 1000;

  for (let attempt = 1; ; attempt += 1) {
    let outcome: HandleOutcome;
    try {
      outcome = await options.consumer.handleMessage(msg.content);
    } catch (err) {
      const error = err instanceof Error ? err.message : String(err);
      const permanent = isNonRetryable(err);
      if (permanent || attempt >= maxAttempts) {
        logger.error('Event dead-lettered', {
          consumer: options.consumer.consumerName,
          messageId: msg.properties?.messageId,
          routingKey: msg.fields?.routingKey,
          attempts: attempt,
          permanent,
          error,
        });
        return settle(() => options.channel.nack(msg, false, false), 'nack', msg, options, logger)
          ? 'DEAD_LETTERED'
          : 'UNSETTLED';
      }
      logger.warn('Event handling failed, retrying', {
        consumer: options.consumer.consumerName,
        messageId: msg.properties?.messageId,
        attempt,
        error,
      });
      await sleep(backoff);
      backoff = Math.min(backoff * multiplier, maxBackoff);
      continue;
    }

    // Outside the try above: a failed ack is a channel problem, not a reason to run the handler again.
    if (!settle(() => options.channel.ack(msg), 'ack', msg, options, logger)) return 'UNSETTLED';
    if (outcome.status === 'DUPLICATE') {
      logger.info('Duplicate event skipped', {
        consumer: options.consumer.consumerName,
        eventId: outcome.envelope.eventId,
        eventType: outcome.envelope.eventType,
      });
      return 'ACK_DUPLICATE';
    }
    return 'ACK_PROCESSED';
  }
}

/** Runs ack/nack; returns false (and logs) instead of throwing when the channel refuses it. */
function settle(
  action: () => void,
  name: 'ack' | 'nack',
  msg: AmqpMessageLike,
  options: ConsumeOptions,
  logger: Logger,
): boolean {
  try {
    action();
    return true;
  } catch (err) {
    logger.error(`Could not ${name} the message; it stays unacked and the broker will redeliver it (dedupe makes that safe)`, {
      consumer: options.consumer.consumerName,
      messageId: msg.properties?.messageId,
      routingKey: msg.fields?.routingKey,
      error: err instanceof Error ? err.message : String(err),
    });
    return false;
  }
}

/** Starts consuming `queue` with manual acks. Returns the consumer tag. */
export async function consumeEvents(options: ConsumeOptions): Promise<string> {
  const logger = options.logger ?? consoleLogger;
  const { consumerTag } = await options.channel.consume(
    options.queue,
    (msg) => {
      if (msg === null) return; // consumer cancelled by broker
      // amqplib does not await this callback, so the promise must never reject unobserved.
      handleDelivery(msg, options).catch((err: unknown) => {
        logger.error('Delivery handling failed; the message stays unacked for redelivery', {
          consumer: options.consumer.consumerName,
          messageId: msg.properties?.messageId,
          routingKey: msg.fields?.routingKey,
          error: err instanceof Error ? err.message : String(err),
        });
      });
    },
    { noAck: false },
  );
  return consumerTag;
}
