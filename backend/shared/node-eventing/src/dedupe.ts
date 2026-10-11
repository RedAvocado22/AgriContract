import { parseEnvelope, type EventEnvelope } from './envelope.js';
import type { ProcessedEventStore, TransactionRunner } from './ports.js';

export type EventHandler<Tx> = (envelope: EventEnvelope, tx: Tx) => Promise<void>;

export type HandleOutcome =
  | { status: 'PROCESSED'; envelope: EventEnvelope }
  | { status: 'DUPLICATE'; envelope: EventEnvelope };

export interface IdempotentConsumerOptions<Tx> {
  /** Stable logical name of this consumer, e.g. "analytics.contract-signed". Part of the dedupe key. */
  consumerName: string;
  transactions: TransactionRunner<Tx>;
  processedEvents: ProcessedEventStore<Tx>;
  handler: EventHandler<Tx>;
}

/**
 * Consumer-side dedupe by envelope eventId (Verification Matrix #7, data-governance §8b).
 *
 * The "already processed" marker and the handler's DB writes commit in ONE transaction:
 *  - handler succeeds  -> marker + side effects committed together;
 *  - handler throws    -> both rolled back, so a retry or DLQ replay processes it again;
 *  - same eventId again (redelivery / DLQ replay of the original eventId) -> skipped, no new side effect.
 *
 * Side effects outside this DB (emails, HTTP calls) are not covered by the transaction;
 * those handlers must also use their own semantic idempotency key (e.g. notification
 * `(event_id, recipient_email, notification_type)`).
 */
export class IdempotentConsumer<Tx> {
  constructor(private readonly options: IdempotentConsumerOptions<Tx>) {
    if (!options.consumerName) throw new Error('consumerName is required');
  }

  get consumerName(): string {
    return this.options.consumerName;
  }

  /** Parses + validates the raw body (throws InvalidEnvelopeError if invalid), then handles it once. */
  async handleMessage(body: Buffer | string | unknown): Promise<HandleOutcome> {
    return this.handle(parseEnvelope(body));
  }

  async handle(envelope: EventEnvelope): Promise<HandleOutcome> {
    const { transactions, processedEvents, handler, consumerName } = this.options;
    return transactions.inTransaction(async (tx) => {
      const isNew = await processedEvents.tryMarkProcessed(tx, consumerName, envelope);
      if (!isNew) return { status: 'DUPLICATE', envelope } as const;
      await handler(envelope, tx);
      return { status: 'PROCESSED', envelope } as const;
    });
  }
}
