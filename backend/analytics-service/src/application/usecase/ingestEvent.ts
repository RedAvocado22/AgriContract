import { INGESTED_EVENT_TYPES } from '../../domain/ingestedEvents';
import type { IngestEventCommand, IngestedEventStore, ProjectionHandler } from '../port';

/** Dedupe identity of an ingest consumer: one idempotency-log namespace per ingested event type. */
export function consumerName(eventType: string): string {
  return `analytics.${eventType}`;
}

/**
 * Use case "ingest one canonical event". Runs inside the caller's DB transaction, which the messaging
 * adapter opens together with the idempotency-log row:
 *  1. persist the validated event in the durable landing table (so nothing that was acked is lost);
 *  2. run the projection handlers registered for that event type, if any (#133 / #148).
 * A failure in either step rolls back the idempotency row too, so the message is retried or dead-lettered.
 */
export class IngestEvent<Tx> {
  constructor(
    private readonly store: IngestedEventStore<Tx>,
    private readonly projections: readonly ProjectionHandler<Tx>[] = [],
  ) {
    const known = new Set<string>(INGESTED_EVENT_TYPES);
    for (const p of projections) {
      if (!known.has(p.eventType)) {
        throw new Error(`No queue is declared for ${p.eventType}; add it to INGESTED_EVENT_TYPES`);
      }
    }
  }

  async execute(command: IngestEventCommand, tx: Tx): Promise<void> {
    await this.store.record(tx, command);
    for (const projection of this.projections) {
      if (projection.eventType === command.eventType) await projection.apply(command, tx);
    }
  }
}
