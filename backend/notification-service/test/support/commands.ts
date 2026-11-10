import type {
  ActivationCancelRequestedCommand,
  ContractActivationFailedRequestedCommand,
  ContractAnchorRequestedCommand,
  NotificationRecipient,
} from '../../src/application/commands';

/*
 * Fixtures for the Phase 2 notification commands
 * (contracts/events/notification-commands.yaml). The envelope is the one
 * contract-service writes to its outbox for a signed contract (#360), so the
 * contract test can hand the same object to Ajv and to the parser.
 */

const CONTRACT_ID = '3f1b9c7e-5d2a-4c8b-9e1f-7a6b5c4d3e2f';

/** The terms snapshot, trimmed to a readable subset of the signed canonical JSON. */
export const TERMS_SNAPSHOT: Record<string, unknown> = {
  agreedPrice: '92000.00',
  currency: 'VND',
  contractTemplateCode: 'VN_COFFEE_B2B',
  contractTemplateVersion: '1.0.0',
  milestoneSchedule: [
    {
      milestoneIndex: 1,
      committedQuantity: 1200,
      expectedDeliveryDate: '2027-01-15',
      graceDays: 3,
    },
  ],
  totalContractValue: '110400000.00',
};

/**
 * `signedContentHash` of `TERMS_SNAPSHOT`, computed the way contract-service
 * does (`JcsTermsCanonicalizer`): `org.erdtman.jcs.JsonCanonicalizer` 1.1, then
 * SHA-256 of the UTF-8 bytes. Written out rather than computed here, so a test
 * comparing against it does not reuse the code it checks.
 */
export const TERMS_SNAPSHOT_HASH =
  '67e2e1076c96f3df33106f897bdac5e0bdd60c2c013a63bd5f336d2fa63b493f';

export const ANCHOR_RECIPIENTS: NotificationRecipient[] = [
  {
    userId: 'd2a1f3b4-6c5d-4e7f-8a9b-0c1d2e3f4a5b',
    email: 'buyer@anchor.test',
    role: 'BUYER',
  },
  {
    userId: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
    email: 'seller@anchor.test',
    role: 'SELLER',
  },
];

/** A valid `notification.contract_anchor_requested` message, as published. */
export function anchorEnvelope(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    eventId: '1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d',
    eventType: 'notification.contract_anchor_requested',
    eventVersion: 1,
    occurredAt: '2026-11-08T09:30:00Z',
    producer: 'contract-service',
    aggregateId: CONTRACT_ID,
    correlationId: CONTRACT_ID,
    causationId: null,
    payload: anchorPayload(),
    ...overrides,
  };
}

export function anchorPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    contractId: CONTRACT_ID,
    recipients: ANCHOR_RECIPIENTS.map((r) => ({ ...r })),
    contractTermsSnapshot: { ...TERMS_SNAPSHOT },
    signedContentHash: TERMS_SNAPSHOT_HASH,
    signedAt: '2026-11-08T09:30:00Z',
    ...overrides,
  };
}

export const anchorBody = (overrides: Record<string, unknown> = {}): Buffer =>
  Buffer.from(JSON.stringify(anchorEnvelope(overrides)));

/** The command the parser is expected to produce from `anchorEnvelope()`. */
export function anchorCommand(): ContractAnchorRequestedCommand {
  const envelope = anchorEnvelope();
  return {
    eventId: envelope.eventId as string,
    contractId: CONTRACT_ID,
    recipients: ANCHOR_RECIPIENTS.map((r) => ({ ...r })),
    contractTermsSnapshot: { ...TERMS_SNAPSHOT },
    signedContentHash: TERMS_SNAPSHOT_HASH,
    signedAt: '2026-11-08T09:30:00Z',
  };
}

/*
 * `notification.activation_cancel_requested`, as contract-service writes it
 * (OutboxContractEventPublisher.activationCancelRequested): the buyer asked,
 * so the one recipient is the seller.
 */
export const CANCEL_RECIPIENT: NotificationRecipient = {
  userId: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
  email: 'seller@cancel.test',
  role: 'SELLER',
};

export function cancelEnvelope(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    eventId: '6c5d4e3f-2a1b-4c0d-9e8f-7a6b5c4d3e2f',
    eventType: 'notification.activation_cancel_requested',
    eventVersion: 1,
    occurredAt: '2026-11-09T03:00:00Z',
    producer: 'contract-service',
    aggregateId: CONTRACT_ID,
    correlationId: CONTRACT_ID,
    causationId: null,
    payload: cancelPayload(),
    ...overrides,
  };
}

export function cancelPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    contractId: CONTRACT_ID,
    recipients: [{ ...CANCEL_RECIPIENT }],
    requestedByRole: 'BUYER',
    oneSidedCancelAllowedFrom: '2026-11-14T03:00:00Z',
    ...overrides,
  };
}

export const cancelBody = (overrides: Record<string, unknown> = {}): Buffer =>
  Buffer.from(JSON.stringify(cancelEnvelope(overrides)));

/** The command the parser is expected to produce from `cancelEnvelope()`. */
export function cancelCommand(): ActivationCancelRequestedCommand {
  return {
    eventId: cancelEnvelope().eventId as string,
    contractId: CONTRACT_ID,
    recipients: [{ ...CANCEL_RECIPIENT }],
    requestedByRole: 'BUYER',
    oneSidedCancelAllowedFrom: '2026-11-14T03:00:00Z',
  };
}

/*
 * `notification.contract_activation_failed_requested`, as contract-service
 * writes it (OutboxContractEventPublisher.contractActivationFailedRequested)
 * from escrow-service's `escrow.deposit_lock_failed`: the buyer's lock failed,
 * the seller's is held, and both parties are addressed.
 */
export const ACTIVATION_FAILED_RECIPIENTS: NotificationRecipient[] = [
  {
    userId: 'd2a1f3b4-6c5d-4e7f-8a9b-0c1d2e3f4a5b',
    email: 'buyer@activation.test',
    role: 'BUYER',
  },
  {
    userId: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
    email: 'seller@activation.test',
    role: 'SELLER',
  },
];

export function activationFailedEnvelope(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    eventId: '4b3a2c1d-0e9f-4a8b-8c7d-6e5f4a3b2c1d',
    eventType: 'notification.contract_activation_failed_requested',
    eventVersion: 1,
    occurredAt: '2026-11-10T02:00:00.123456Z',
    producer: 'contract-service',
    aggregateId: CONTRACT_ID,
    correlationId: CONTRACT_ID,
    causationId: '7d6c5b4a-3f2e-4d1c-8b0a-9f8e7d6c5b4a',
    payload: activationFailedPayload(),
    ...overrides,
  };
}

export function activationFailedPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    contractId: CONTRACT_ID,
    recipients: ACTIVATION_FAILED_RECIPIENTS.map((r) => ({ ...r })),
    failedLeg: 'BUYER',
    buyerDepositState: 'LOCK_FAILED',
    sellerDepositState: 'DEPOSIT_LOCKED',
    nextActions: ['RETRY_DEPOSIT_LOCK', 'REQUEST_ACTIVATION_CANCEL'],
    oneSidedCancelAllowedFrom: '2026-11-13T17:00:00Z',
    ...overrides,
  };
}

export const activationFailedBody = (
  overrides: Record<string, unknown> = {},
): Buffer => Buffer.from(JSON.stringify(activationFailedEnvelope(overrides)));

/** The command the parser is expected to produce from `activationFailedEnvelope()`. */
export function activationFailedCommand(): ContractActivationFailedRequestedCommand {
  return {
    eventId: activationFailedEnvelope().eventId as string,
    contractId: CONTRACT_ID,
    recipients: ACTIVATION_FAILED_RECIPIENTS.map((r) => ({ ...r })),
    failedLeg: 'BUYER',
    buyerDepositState: 'LOCK_FAILED',
    sellerDepositState: 'DEPOSIT_LOCKED',
    nextActions: ['RETRY_DEPOSIT_LOCK', 'REQUEST_ACTIVATION_CANCEL'],
    oneSidedCancelAllowedFrom: '2026-11-13T17:00:00Z',
  };
}
