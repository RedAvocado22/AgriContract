/**
 * What the ingest use case needs from the outside, and what it receives. The messaging adapter validates
 * the RabbitMQ message and maps it to an IngestEventCommand, so nothing in application/ sees a raw
 * broker payload (rules §3, §4B).
 */

/** A canonical event that already passed envelope and payload validation at the messaging boundary. */
export interface IngestEventCommand {
  eventId: string;
  eventType: string;
  eventVersion: number;
  producer: string;
  aggregateId: string;
  occurredAt: Date;
  correlationId: string | null;
  causationId: string | null;
  /** Validated payload; contact PII (recipient e-mail addresses) is removed before this point. */
  payload: Readonly<Record<string, unknown>>;
}

/**
 * Durable landing table for every ingested event (analytics_ingested_event). Written inside the same
 * DB transaction as the idempotency-log row, so an event is either fully ingested or not at all.
 */
export interface IngestedEventStore<Tx> {
  record(tx: Tx, command: IngestEventCommand): Promise<void>;
}

/**
 * Projection work for one event type (fact and dimension tables). Runs in the same transaction as the
 * ingest write. Registered by #133 and #148; none is registered yet, and events are ingested anyway.
 */
export interface ProjectionHandler<Tx> {
  readonly eventType: string;
  apply(command: IngestEventCommand, tx: Tx): Promise<void>;
}
