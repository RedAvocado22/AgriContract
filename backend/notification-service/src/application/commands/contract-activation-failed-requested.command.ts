import type { NotificationRecipient } from './notification-recipient';

/** Whose deposit could not be locked: the contract's `failedLeg` enum. */
export const FAILED_LEGS = ['BUYER', 'SELLER', 'BOTH'] as const;
export type FailedLeg = (typeof FAILED_LEGS)[number];

/** What may happen next: the contract's `nextActions.items` enum. */
export const ACTIVATION_NEXT_ACTIONS = [
  'RETRY_DEPOSIT_LOCK',
  'REQUEST_ACTIVATION_CANCEL',
] as const;
export type ActivationNextAction = (typeof ACTIVATION_NEXT_ACTIONS)[number];

/**
 * `notification.contract_activation_failed_requested`
 * (notification-commands.yaml `contractActivationFailedRequested`, D42),
 * published by contract-service when escrow-service reports that the deposits
 * of a SIGNED contract could not be locked (`escrow.deposit_lock_failed`). It
 * goes to both parties; a later report of the same contract (a retry that
 * failed again) is a new command with its own eventId.
 *
 * `eventId` is the envelope's: the dedup key's event (design §5).
 */
export interface ContractActivationFailedRequestedCommand {
  eventId: string;
  contractId: string;
  recipients: NotificationRecipient[];
  failedLeg: FailedLeg;
  /**
   * The state of each deposit leg as escrow-service reported it. The contract
   * declares a plain string; escrow-service writes DEPOSIT_LOCKED, LOCK_FAILED
   * or NOT_REQUIRED (OutboxDepositEventPublisher.depositLockFailed).
   */
  buyerDepositState: string;
  sellerDepositState: string;
  nextActions: ActivationNextAction[];
  /** From this instant one party's cancel request is enough. */
  oneSidedCancelAllowedFrom: string;
}
