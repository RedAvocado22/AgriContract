import { Inject, Injectable } from '@nestjs/common';
import { isDuplicateKey } from '@agricontract/eventing';
import type { Pool, ResultSetHeader, RowDataPacket } from 'mysql2/promise';
import type {
  ClaimResult,
  NotificationLogRepository,
} from '../../application/ports/notification-log.repository';
import type {
  NotificationKey,
  NotificationLog,
} from '../../domain/notification-log';
import { MYSQL_POOL } from './mysql.provider';

/** Recipient prefix and notification type that migration V2 gave every row written before it. */
export const LEGACY_RECIPIENT_PREFIX = 'legacy:';
export const LEGACY_NOTIFICATION_TYPE = 'LEGACY_P1';

/** The row of one email: the columns of uq_event_recipient_type. */
const BY_KEY = 'event_id = ? AND recipient_email = ? AND notification_type = ?';
const keyParams = (key: NotificationKey) => [
  key.eventId,
  key.recipientEmail,
  key.notificationType,
];

/**
 * `notification_logs` (schema V2). One row per email, unique by
 * `(event_id, recipient_email, notification_type)`. `user_id` still receives
 * the recipient email, as before V2; `recipient_user_id` holds the platform id
 * of the recipient when the sender knows it (null otherwise), and
 * `template_version` the template that rendered the mail. `provider_message_id`
 * is left to its default for now.
 * `updated_at` doubles as the claim timestamp; all timestamps come from the
 * database clock in UTC (UTC_TIMESTAMP), so the lease does not depend on the
 * app host clock.
 */
@Injectable()
export class MysqlNotificationLogRepository implements NotificationLogRepository {
  constructor(@Inject(MYSQL_POOL) private readonly pool: Pool) {}

  async claim(log: NotificationLog, leaseMs: number): Promise<ClaimResult> {
    if (await this.sentBeforeV2(log)) return 'ALREADY_SENT';
    if (await this.insertPending(log)) return 'CLAIMED';

    // The row exists. Retake it only if FAILED or if its PENDING claim is stale;
    // the row lock makes this check-and-set atomic between concurrent callers.
    const [result] = await this.pool.query<ResultSetHeader>(
      `UPDATE notification_logs
          SET status = 'PENDING', updated_at = UTC_TIMESTAMP(6)
        WHERE ${BY_KEY}
          AND (status = 'FAILED'
               OR (status = 'PENDING'
                   AND updated_at < UTC_TIMESTAMP(6) - INTERVAL ? MICROSECOND))`,
      [...keyParams(log.key), leaseMs * 1000],
    );
    if (result.affectedRows === 1) return 'CLAIMED';

    const [rows] = await this.pool.query<RowDataPacket[]>(
      `SELECT status FROM notification_logs WHERE ${BY_KEY}`,
      keyParams(log.key),
    );
    return rows[0]?.status === 'SENT' ? 'ALREADY_SENT' : 'IN_PROGRESS';
  }

  async heartbeat(key: NotificationKey): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET updated_at = UTC_TIMESTAMP(6)
        WHERE ${BY_KEY} AND status = 'PENDING'`,
      keyParams(key),
    );
  }

  async markSent(key: NotificationKey): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET status = 'SENT', sent_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6)
        WHERE ${BY_KEY}`,
      keyParams(key),
    );
  }

  async markFailed(key: NotificationKey, failureReason: string): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET status = 'FAILED', retry_count = retry_count + 1, failure_reason = ?,
              updated_at = UTC_TIMESTAMP(6)
        WHERE ${BY_KEY}`,
      [failureReason, ...keyParams(key)],
    );
  }

  /**
   * Whether a row written before V2 shows this event already SENT to this
   * recipient. V2 moved those rows to recipient `legacy:<email>` and type
   * LEGACY_P1 (Phase 1 sent one email per event and recipient), and nothing
   * writes them any more, so reading them races with no one. Looked up on
   * uq_event_recipient_type.
   */
  private async sentBeforeV2(log: NotificationLog): Promise<boolean> {
    const [rows] = await this.pool.query<RowDataPacket[]>(
      `SELECT 1 FROM notification_logs
        WHERE ${BY_KEY} AND status = 'SENT'`,
      [
        log.eventId,
        LEGACY_RECIPIENT_PREFIX + log.recipientEmail,
        LEGACY_NOTIFICATION_TYPE,
      ],
    );
    return rows.length > 0;
  }

  /** Inserts the log as PENDING; false when its key already exists. */
  private async insertPending(log: NotificationLog): Promise<boolean> {
    try {
      await this.pool.query(
        `INSERT INTO notification_logs
           (notification_id, event_id, event_type, notification_type, user_id, recipient_user_id,
            recipient_email, channel, subject, body, template_version, status, retry_count,
            created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
        [
          log.notificationId,
          log.eventId,
          log.eventType,
          log.notificationType,
          log.recipientEmail,
          log.recipientUserId,
          log.recipientEmail,
          log.channel,
          log.subject,
          log.body,
          log.templateVersion,
        ],
      );
      return true;
    } catch (err) {
      if (isDuplicateKey(err)) return false;
      throw err;
    }
  }
}
