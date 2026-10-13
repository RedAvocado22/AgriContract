import {
  consumeEvents,
  type AmqpChannelLike,
  type Logger,
  type ProcessedEventStore,
  type TransactionRunner,
} from '@agricontract/eventing';
import type { IngestionHandler } from '../../application/port.js';
import { buildConsumer } from '../../application/usecase/ingestEvent.js';
import { SUBSCRIPTIONS, queueName } from './topology.js';

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

/**
 * Starts one RabbitMQ consumer (manual ack, DLQ on failure) per registered handler. Subscribed event
 * types WITHOUT a handler are not consumed: their durable queues keep buffering until the handler
 * ships, then catch up.
 */
export async function startIngestion<Tx>(options: IngestionOptions<Tx>): Promise<string[]> {
  const subscribed = new Set(SUBSCRIPTIONS.map((s) => s.eventType));
  const seen = new Set<string>();
  const tags: string[] = [];

  for (const handler of options.handlers) {
    if (!subscribed.has(handler.eventType)) {
      throw new Error(`No queue is declared for ${handler.eventType}; add it to INGESTED_EVENT_TYPES`);
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
