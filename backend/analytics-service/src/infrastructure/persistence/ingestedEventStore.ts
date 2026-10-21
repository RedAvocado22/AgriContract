import type { MysqlConnectionLike } from '@agricontract/eventing';
import type { IngestEventCommand, IngestedEventStore } from '../../application/port';

/** DATETIME(3) text in UTC; the pool is created with timezone 'Z'. */
export function toMysqlDateTime(date: Date): string {
  return date.toISOString().replace('T', ' ').replace('Z', '');
}

/** Writes the validated event to analytics_ingested_event inside the caller's transaction. */
export class MysqlIngestedEventStore implements IngestedEventStore<MysqlConnectionLike> {
  async record(tx: MysqlConnectionLike, command: IngestEventCommand): Promise<void> {
    await tx.query(
      `INSERT INTO analytics_ingested_event
         (event_id, event_type, event_version, producer, aggregate_id, correlation_id, causation_id, occurred_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        command.eventId,
        command.eventType,
        command.eventVersion,
        command.producer,
        command.aggregateId,
        command.correlationId,
        command.causationId,
        toMysqlDateTime(command.occurredAt),
        JSON.stringify(command.payload),
      ],
    );
  }
}
