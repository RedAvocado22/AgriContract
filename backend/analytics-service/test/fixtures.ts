import { randomUUID } from 'node:crypto';
import { createEnvelope, type EventEnvelope } from '@agricontract/eventing';

/** One schema-valid payload per ingested event type (fresh ids each call). Kept next to the YAML contract test. */
export function validPayload(eventType: string): Record<string, unknown> {
  const contractId = randomUUID();
  const milestoneId = randomUUID();
  switch (eventType) {
    case 'contract.signed':
      return {
        contractId,
        listingId: randomUUID(),
        reservedQuantity: 1500.5,
        commodity: 'COFFEE',
        buyerId: randomUUID(),
        sellerId: randomUUID(),
        totalAmount: '120000000.00',
        buyerDepositAmount: '12000000.00',
        sellerDepositAmount: '12000000.00',
        signedContentHash: 'a'.repeat(64),
        signedAt: '2026-10-12T05:00:00Z',
      };
    case 'milestone.settled':
      return {
        contractId,
        milestoneId,
        lockedAmount: '60000000.00',
        actualAmount: '59500000.00',
        recipients: [
          { userId: randomUUID(), email: 'buyer@example.com', role: 'BUYER' },
          { userId: randomUUID(), email: 'seller@example.com', role: 'SELLER' },
        ],
      };
    case 'milestone.cancelled_with_penalty':
      return {
        contractId,
        milestoneId,
        remedyDecisionId: randomUUID(),
        finalBreachingRole: 'SELLER',
        breachReasonCode: 'LATE_DELIVERY',
        penaltyAmount: '1000000.00',
      };
    case 'milestone.force_majeure_resolved':
      return { contractId, milestoneId, forceMajeureClaimId: randomUUID(), decision: 'APPROVED' };
    case 'milestone.price_adjusted':
      return {
        contractId,
        milestoneId,
        baseUnitPrice: '120000.00',
        effectiveUnitPrice: '125000.00',
        currency: 'VND',
        proposerId: randomUUID(),
        accepterId: randomUUID(),
        acceptedAt: '2026-10-12T05:00:00Z',
      };
    case 'contract.settled':
      return { contractId, buyerId: randomUUID(), sellerId: randomUUID() };
    case 'contract.terminated':
      return {
        contractId,
        terminationType: 'TERMINATION_FOR_BREACH',
        requestedBy: 'BUYER',
        finalBreachingRole: 'SELLER',
        breachReasonCode: 'NON_DELIVERY',
        remedyDecisionId: randomUUID(),
        breachCaseId: randomUUID(),
        affectedMilestoneIds: [milestoneId],
        supersededByContractId: null,
        replacesContractId: null,
      };
    default:
      throw new Error(`no fixture for ${eventType}`);
  }
}

/** A canonical envelope as contract-service publishes it, valid unless overridden. */
export function validEvent(
  eventType: string,
  aggregateId: string = randomUUID(),
  overrides: { payload?: Record<string, unknown>; producer?: string; eventVersion?: number } = {},
): EventEnvelope {
  return createEnvelope({
    eventType,
    producer: overrides.producer ?? 'contract-service',
    eventVersion: overrides.eventVersion ?? 1,
    aggregateId,
    correlationId: `saga-${aggregateId}`,
    payload: overrides.payload ?? validPayload(eventType),
  });
}
