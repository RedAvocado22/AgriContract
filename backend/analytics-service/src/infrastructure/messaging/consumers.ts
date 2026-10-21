import {
  IdempotentConsumer,
  handleDelivery,
  parseEnvelope,
  type AmqpChannelLike,
  type AmqpMessageLike,
  type ConsumeOptions,
  type Logger,
  type MessageConsumer,
  type ProcessedEventStore,
  type TransactionRunner,
} from '@agricontract/eventing';
import { consumerName, type IngestEvent } from '../../application/usecase/ingestEvent';
import { toIngestCommand } from './commandMapper';
import { InvalidEventError, validateCanonicalEvent } from './eventContracts';
import { SUBSCRIPTIONS, queueName } from './topology';

/**
 * One consumer for one event type. Order matters, and nothing is committed before step 2 passes:
 *  1. parse the envelope (invalid -> InvalidEnvelopeError, dead-lettered without retry);
 *  2. validate producer, version and payload against the frozen contract (invalid -> InvalidEventError,
 *     dead-lettered without retry; the handler never runs and no idempotency row is written);
 *  3. in ONE DB transaction: idempotency-log row + ingest use case (landing table + projections).
 */
export function buildConsumer<Tx>(
  eventType: string,
  ingest: IngestEvent<Tx>,
  transactions: TransactionRunner<Tx>,
  processedEvents: ProcessedEventStore<Tx>,
): MessageConsumer {
  const dedupe = new IdempotentConsumer<Tx>({
    consumerName: consumerName(eventType),
    transactions,
    processedEvents,
    handler: (envelope, tx) => ingest.execute(toIngestCommand(envelope), tx),
  });
  return {
    consumerName: dedupe.consumerName,
    async handleMessage(body: Buffer) {
      const envelope = parseEnvelope(body);
      const errors = validateCanonicalEvent(envelope, eventType);
      if (errors.length > 0) throw new InvalidEventError(eventType, errors);
      return dedupe.handle(envelope);
    },
  };
}

/** Counts deliveries being handled (handler, retries and ack) so shutdown can wait for them. */
export class InFlightTracker {
  private active = 0;
  private waiters: Array<() => void> = [];

  get count(): number {
    return this.active;
  }

  async run<T>(work: () => Promise<T>): Promise<T> {
    this.active += 1;
    try {
      return await work();
    } finally {
      this.active -= 1;
      if (this.active === 0) {
        const waiting = this.waiters;
        this.waiters = [];
        for (const wake of waiting) wake();
      }
    }
  }

  /** Resolves true once nothing is in flight, or false after `timeoutMs`. */
  idle(timeoutMs: number): Promise<boolean> {
    if (this.active === 0) return Promise.resolve(true);
    return new Promise((resolve) => {
      const wake = () => {
        clearTimeout(timer);
        resolve(true);
      };
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((w) => w !== wake);
        resolve(false);
      }, timeoutMs);
      this.waiters.push(wake);
    });
  }
}

export interface IngestionOptions<Tx> {
  channel: AmqpChannelLike;
  transactions: TransactionRunner<Tx>;
  processedEvents: ProcessedEventStore<Tx>;
  ingest: IngestEvent<Tx>;
  logger: Logger;
  /** Retries for transient errors before the message is dead-lettered (Java services: 3). */
  maxAttempts?: number;
  sleep?: (ms: number) => Promise<void>;
}

export interface IngestionHandle {
  consumerTags: string[];
  inFlight: InFlightTracker;
  /** Cancels every consumer; deliveries that arrive afterwards are requeued untouched. */
  stopConsuming(): Promise<void>;
}

/**
 * Starts one RabbitMQ consumer (manual ack, DLQ on failure) for EVERY subscribed event type, whether or
 * not a projection exists for it yet, so a restart always drains the durable backlog.
 */
export async function startIngestion<Tx>(options: IngestionOptions<Tx>): Promise<IngestionHandle> {
  const { channel, logger } = options;
  const inFlight = new InFlightTracker();
  const consumerTags: string[] = [];
  let stopping = false;

  for (const { eventType } of SUBSCRIPTIONS) {
    const queue = queueName(eventType);
    const delivery: ConsumeOptions = {
      channel,
      queue,
      consumer: buildConsumer(eventType, options.ingest, options.transactions, options.processedEvents),
      maxAttempts: options.maxAttempts ?? 3,
      logger,
      ...(options.sleep ? { sleep: options.sleep } : {}),
    };
    const { consumerTag } = await channel.consume(
      queue,
      (msg: AmqpMessageLike | null) => {
        if (msg === null) {
          logger.warn('Consumer cancelled by the broker', { queue });
          return;
        }
        if (stopping) {
          // Arrived after shutdown began: hand it back untouched for the next instance.
          try {
            channel.nack(msg, false, true);
          } catch {
            // channel already closed: the broker redelivers unacked messages anyway
          }
          return;
        }
        // amqplib does not await this callback, so the promise must never reject unobserved.
        inFlight.run(() => handleDelivery(msg, delivery)).catch((err: unknown) => {
          logger.error('Delivery handling failed; the message stays unacked for redelivery', {
            queue,
            error: err instanceof Error ? err.message : String(err),
          });
        });
      },
      { noAck: false },
    );
    consumerTags.push(consumerTag);
    logger.info('Analytics consumer started', { eventType, queue });
  }

  return {
    consumerTags,
    inFlight,
    async stopConsuming() {
      stopping = true;
      for (const tag of consumerTags) {
        try {
          await channel.cancel?.(tag);
        } catch (err) {
          logger.warn('Could not cancel consumer', { consumerTag: tag, error: err instanceof Error ? err.message : String(err) });
        }
      }
    },
  };
}
