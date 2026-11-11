/** What happened to the user's reputation lock: the contract's `action` enum. */
export const LOCK_ACTIONS = ['LOCKED', 'UNLOCKED'] as const;
export type LockAction = (typeof LOCK_ACTIONS)[number];

/**
 * `notification.user_lock_changed_requested` (notification-commands.yaml
 * `userLockChangedRequested`), published by user-service once it has applied
 * a `reputation.locked` or `reputation.unlocked` from reputation-service
 * (OutboxLockChangePublisher). It goes to that user alone, at
 * `recipientEmail`; there is no `payload.recipients`.
 *
 * `eventId` is the envelope's: the dedup key's event (design §5). Every
 * applied change is a new command; a stale revision user-service ignores
 * publishes nothing.
 */
export interface UserLockChangedRequestedCommand {
  eventId: string;
  userId: string;
  recipientEmail: string;
  action: LockAction;
  /**
   * The lock stored after the change, not the incoming value: for LOCKED the
   * later of the old and the new end (user-service never sends null), for
   * UNLOCKED the end of a lock that still applies, null when none is left.
   * Absent is read as null: the contract does not require the property.
   */
  effectiveUntil: string | null;
  /** reputation-service's code, any string: quoted as sent, never interpreted. */
  reasonCode: string;
}
