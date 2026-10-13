import {
  IdempotentConsumer,
  NonRetryableError,
  type ProcessedEventStore,
  type TransactionRunner,
} from '@agricontract/eventing';
import type { IngestionHandler } from '../port.js';

/** Dedupe identity of a projection: one idempotency-log namespace per ingested event type. */
export function consumerName(eventType: string): string {
  return `analytics.${eventType}`;
}

/**
 * Use case "ingest one canonical event": dedupe by eventId in analytics_idempotency_log and run the
 * projection in the same transaction. An event of another type than the handler's is never guessed:
 * it is rejected as non-retryable so the messaging layer parks it in the DLQ.
 */
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
