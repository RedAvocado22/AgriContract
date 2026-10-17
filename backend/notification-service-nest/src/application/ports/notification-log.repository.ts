import type { NotificationLog } from '../../domain/notification-log';

export const NOTIFICATION_LOG_REPOSITORY = Symbol(
  'NOTIFICATION_LOG_REPOSITORY',
);

export interface NotificationLogRepository {
  findByEventIdAndRecipient(
    eventId: string,
    recipientEmail: string,
  ): Promise<NotificationLog | null>;

  /**
   * Inserts a new log. Resolves `false` when `(event_id, user_id)` already
   * exists: another delivery of the same event claimed this recipient first.
   */
  insert(log: NotificationLog): Promise<boolean>;

  /** Saves status and retry count of an existing log. */
  update(log: NotificationLog): Promise<void>;
}
