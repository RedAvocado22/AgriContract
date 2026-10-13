import {
  IdempotentConsumer,
  NonRetryableError,
  consumeEvents,
  type AmqpChannelLike,
  type EventEnvelope,
  type Logger,
  type ProcessedEventStore,
  type TransactionRunner,
} from '@agricontract/eventing';
import { SUBSCRIPTIONS, queueName } from './topology.js';

/**
 * A projection handler for one canonical event type. It receives the validated envelope and the
 * open DB transaction; its writes commit atomically with the idempotency-log row, so a crash or
 * failure rolls both back and the redelivery is processed again, while a successful event is
 * never applied twice (analytics design §2.3, §3).
 */
export interface IngestionHandler<Tx> {
  eventType: string;
  handle(envelope: EventEnvelope, tx: Tx): Promise<void>;
}

export interface IngestionOptions<Tx> {
  channel: AmqpChannelLike;
  transactions: TransactionRunner<Tx>;
  processedEvents: ProcessedEventStore<Tx>;
  handlers: readonly IngestionHandler<Tx>[];
  logger: Logger;
  /** Retries for transient errors before the message is dead-lettered (Java services: 3). */
  maxAttempts?: number;
  sleep?: (ms: number) => Promise<void>;
}

export function consumerName(eventType: string): string {
  return `analytics.${eventType}`;
}

/** Builds the dedupe-wrapped consumer for one handler (exported for tests). */
export function buildConsumer<Tx>(
  handler: IngestionHandler<Tx>,
  transactions: TransactionRunner<Tx>,
  processedEvents: ProcessedEventStore<Tx>,
): IdempotentConsumer<Tx> {
  return new IdempotentConsumer<Tx>({
    consumerName: consumerName(handler.eventType),
    transactions,
    processedEvents,
    handler: async (envelope, tx) => {
      if (envelope.eventType !== handler.eventType) {
        // Wrong routing: never guess, park it in the DLQ for a developer.
        throw new NonRetryableError(
          `Queue for ${handler.eventType} received ${envelope.eventType} (eventId ${envelope.eventId})`,
        );
      }
      await handler.handle(envelope, tx);
    },
  });
}

/**
 * Starts one consumer per registered handler. Subscribed event types WITHOUT a handler are not
 * consumed: their durable queues keep buffering until the handler ships, then catch up.
 */
export async function startIngestion<Tx>(options: IngestionOptions<Tx>): Promise<string[]> {
  const subscribed = new Set(SUBSCRIPTIONS.map((s) => s.eventType));
  const seen = new Set<string>();
  const tags: string[] = [];

  for (const handler of options.handlers) {
    if (!subscribed.has(handler.eventType)) {
      throw new Error(`No queue is declared for ${handler.eventType}; add it to SUBSCRIPTIONS`);
    }
    if (seen.has(handler.eventType)) throw new Error(`Duplicate handler for ${handler.eventType}`);
    seen.add(handler.eventType);

    const tag = await consumeEvents({
      channel: options.channel,
      queue: queueName(handler.eventType),
      consumer: buildConsumer(handler, options.transactions, options.processedEvents),
      maxAttempts: options.maxAttempts ?? 3,
      logger: options.logger,
      ...(options.sleep ? { sleep: options.sleep } : {}),
    });
    tags.push(tag);
    options.logger.info('Analytics consumer started', { eventType: handler.eventType, queue: queueName(handler.eventType) });
  }

  const buffering = [...subscribed].filter((t) => !seen.has(t));
  if (buffering.length > 0) {
    options.logger.info('Subscribed events buffering in durable queues until their handler ships', { buffering });
  }
  return tags;
}
