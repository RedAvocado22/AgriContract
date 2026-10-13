import type { EventEnvelope } from '@agricontract/eventing';

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
