/**
 * Canonical events analytics ingests (analytics design §3, integration-map "analytics" consumer rows).
 * All are published by contract-service. Each one gets a durable queue from the first deploy, so events
 * start buffering immediately even before their projection handler exists (handlers arrive with
 * #133 / #148). This narrows the "no historical backfill" gap in design §5.
 */
export const INGESTED_EVENT_TYPES = [
  'contract.signed',
  'milestone.settled',
  'milestone.cancelled_with_penalty',
  'milestone.force_majeure_resolved',
  'milestone.price_adjusted',
  'contract.settled',
  'contract.terminated',
] as const;

export type IngestedEventType = (typeof INGESTED_EVENT_TYPES)[number];
