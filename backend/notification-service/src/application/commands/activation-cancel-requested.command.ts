import type { NotificationRecipient } from './notification-recipient';

/** The party who asked to cancel: the contract's `requestedByRole` enum. */
export const CANCEL_REQUESTER_ROLES = ['BUYER', 'SELLER'] as const;
export type CancelRequesterRole = (typeof CANCEL_REQUESTER_ROLES)[number];

/**
 * `notification.activation_cancel_requested` (notification-commands.yaml
 * `activationCancelRequested`, D42), published by contract-service when one
 * party records a cancel request on a SIGNED contract whose deposit lock
 * failed. It goes to the other party (contract-service addresses only them
 * today; OPERATOR is reached its own way), so that staying silent until
 * `oneSidedCancelAllowedFrom` is an informed choice.
 *
 * `eventId` is the envelope's: the dedup key's event (design §5).
 */
export interface ActivationCancelRequestedCommand {
  eventId: string;
  contractId: string;
  recipients: NotificationRecipient[];
  requestedByRole: CancelRequesterRole;
  /** From this instant one party's request is enough to cancel. */
  oneSidedCancelAllowedFrom: string;
}
