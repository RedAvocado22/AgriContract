import type { EventEnvelope } from '@agricontract/eventing';
import type { IngestEventCommand } from '../../application/port';

/**
 * Maps a validated canonical envelope to the application command. Analytics never needs contact data, so
 * the recipient e-mail addresses that milestone.settled carries for the notification flow (contract
 * x-pii-exception) are dropped here and never reach the database; userId and role are kept.
 */
export function toIngestCommand(envelope: EventEnvelope): IngestEventCommand {
  return {
    eventId: envelope.eventId,
    eventType: envelope.eventType,
    eventVersion: envelope.eventVersion,
    producer: envelope.producer,
    aggregateId: envelope.aggregateId,
    occurredAt: new Date(envelope.occurredAt),
    correlationId: envelope.correlationId ?? null,
    causationId: envelope.causationId ?? null,
    payload: withoutContactPii(envelope.payload),
  };
}

function withoutContactPii(payload: Record<string, unknown>): Record<string, unknown> {
  const { recipients, ...rest } = payload;
  if (!Array.isArray(recipients)) return payload;
  return {
    ...rest,
    recipients: recipients.map((r) => {
      const { email: _email, ...kept } = r as Record<string, unknown>;
      return kept;
    }),
  };
}
