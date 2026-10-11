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

export type DeliveryResult = 'ACK_PROCESSED' | 'ACK_DUPLICATE' | 'DEAD_LETTERED';

/**
 * Handles one delivery: ack on success or duplicate; nack(requeue=false) so the queue's
 * dead-letter exchange routes it to the DLQ when the envelope is invalid, the handler
 * throws NonRetryableError, or transient errors exhaust `maxAttempts`.
 * The queue must be declared with `x-dead-letter-exchange` (same as the Java services).
 */
export async function handleDelivery(msg: AmqpMessageLike, options: ConsumeOptions): Promise<DeliveryResult> {
  const logger = options.logger ?? consoleLogger;
  const maxAttempts = Math.max(1, options.maxAttempts ?? 3);
  const multiplier = options.backoffMultiplier ?? 2;
  const maxBackoff = options.maxBackoffMs ?? 4000;
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  let backoff = options.initialBackoffMs ?? 1000;

  for (let attempt = 1; ; attempt += 1) {
    try {
      const outcome = await options.consumer.handleMessage(msg.content);
      options.channel.ack(msg);
      if (outcome.status === 'DUPLICATE') {
        logger.info('Duplicate event skipped', {
          consumer: options.consumer.consumerName,
          eventId: outcome.envelope.eventId,
          eventType: outcome.envelope.eventType,
        });
        return 'ACK_DUPLICATE';
      }
      return 'ACK_PROCESSED';
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
        options.channel.nack(msg, false, false);
        return 'DEAD_LETTERED';
      }
      logger.warn('Event handling failed, retrying', {
        consumer: options.consumer.consumerName,
        messageId: msg.properties?.messageId,
        attempt,
        error,
      });
      await sleep(backoff);
      backoff = Math.min(backoff * multiplier, maxBackoff);
    }
  }
}

/** Starts consuming `queue` with manual acks. Returns the consumer tag. */
export async function consumeEvents(options: ConsumeOptions): Promise<string> {
  const { consumerTag } = await options.channel.consume(
    options.queue,
    (msg) => {
      if (msg === null) return; // consumer cancelled by broker
      void handleDelivery(msg, options);
    },
    { noAck: false },
  );
  return consumerTag;
}
