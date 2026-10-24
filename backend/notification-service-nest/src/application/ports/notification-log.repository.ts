import type { NotificationLog } from '../../domain/notification-log';

export const NOTIFICATION_LOG_REPOSITORY = Symbol(
  'NOTIFICATION_LOG_REPOSITORY',
);

/**
 * Outcome of claiming the send of one email (one event, one recipient):
 * - CLAIMED: this delivery may send now;
 * - ALREADY_SENT: the email went out before, nothing to do;
 * - IN_PROGRESS: another delivery claimed it less than a lease ago and may
 *   still be sending.
 */
export type ClaimResult = 'CLAIMED' | 'ALREADY_SENT' | 'IN_PROGRESS';

export interface NotificationLogRepository {
  /**
   * Atomically takes the right to send `log` for (eventId, recipient): inserts
   * it as PENDING, or turns an existing FAILED row, or a PENDING row older
   * than `leaseMs`, back to a fresh PENDING. At most one concurrent caller
   * gets CLAIMED. No transaction is held afterwards (the send happens outside).
   */
  claim(log: NotificationLog, leaseMs: number): Promise<ClaimResult>;

  /**
   * Keeps a claim alive while its email is being sent: moves `updated_at` of
   * the PENDING row to now (database clock), so the claim stays younger than
   * the lease. Does nothing once the row is SENT or FAILED.
   */
  heartbeat(eventId: string, recipientEmail: string): Promise<void>;

  /** Records the claimed email as SENT. */
  markSent(eventId: string, recipientEmail: string): Promise<void>;

  /** Records the claimed email as FAILED and counts the failure (retry_count + 1). */
  markFailed(eventId: string, recipientEmail: string): Promise<void>;
}
