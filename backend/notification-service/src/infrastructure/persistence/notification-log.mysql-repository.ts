import { Inject, Injectable } from '@nestjs/common';
import { isDuplicateKey } from '@agricontract/eventing';
import type { Pool, ResultSetHeader, RowDataPacket } from 'mysql2/promise';
import type {
  ClaimResult,
  NotificationLogRepository,
} from '../../application/ports/notification-log.repository';
import type { NotificationLog } from '../../domain/notification-log';
import { MYSQL_POOL } from './mysql.provider';

/**
 * `notification_logs` (V1 schema, unchanged). As in the Java service the
 * `user_id` column holds the recipient email, and `(event_id, user_id)` is
 * unique. `updated_at` doubles as the claim timestamp; all timestamps come
 * from the database clock in UTC (UTC_TIMESTAMP), so the lease does not
 * depend on the app host clock.
 */
@Injectable()
export class MysqlNotificationLogRepository implements NotificationLogRepository {
  constructor(@Inject(MYSQL_POOL) private readonly pool: Pool) {}

  async claim(log: NotificationLog, leaseMs: number): Promise<ClaimResult> {
    if (await this.insertPending(log)) return 'CLAIMED';

    // The row exists. Retake it only if FAILED or if its PENDING claim is stale;
    // the row lock makes this check-and-set atomic between concurrent callers.
    const [result] = await this.pool.query<ResultSetHeader>(
      `UPDATE notification_logs
          SET status = 'PENDING', updated_at = UTC_TIMESTAMP(6)
        WHERE event_id = ? AND user_id = ?
          AND (status = 'FAILED'
               OR (status = 'PENDING'
                   AND updated_at < UTC_TIMESTAMP(6) - INTERVAL ? MICROSECOND))`,
      [log.eventId, log.recipientEmail, leaseMs * 1000],
    );
    if (result.affectedRows === 1) return 'CLAIMED';

    const [rows] = await this.pool.query<RowDataPacket[]>(
      `SELECT status FROM notification_logs WHERE event_id = ? AND user_id = ?`,
      [log.eventId, log.recipientEmail],
    );
    return rows[0]?.status === 'SENT' ? 'ALREADY_SENT' : 'IN_PROGRESS';
  }

  async heartbeat(eventId: string, recipientEmail: string): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET updated_at = UTC_TIMESTAMP(6)
        WHERE event_id = ? AND user_id = ? AND status = 'PENDING'`,
      [eventId, recipientEmail],
    );
  }

  async markSent(eventId: string, recipientEmail: string): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET status = 'SENT', updated_at = UTC_TIMESTAMP(6)
        WHERE event_id = ? AND user_id = ?`,
      [eventId, recipientEmail],
    );
  }

  async markFailed(eventId: string, recipientEmail: string): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET status = 'FAILED', retry_count = retry_count + 1, updated_at = UTC_TIMESTAMP(6)
        WHERE event_id = ? AND user_id = ?`,
      [eventId, recipientEmail],
    );
  }

  /** Inserts the log as PENDING; false when (event_id, user_id) already exists. */
  private async insertPending(log: NotificationLog): Promise<boolean> {
    try {
      await this.pool.query(
        `INSERT INTO notification_logs
           (notification_id, event_id, user_id, channel, subject, body, status, retry_count, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
        [
          log.notificationId,
          log.eventId,
          log.recipientEmail,
          log.channel,
          log.subject,
          log.body,
        ],
      );
      return true;
    } catch (err) {
      if (isDuplicateKey(err)) return false;
      throw err;
    }
  }
}
