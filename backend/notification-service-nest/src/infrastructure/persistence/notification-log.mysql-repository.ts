import { Inject, Injectable } from '@nestjs/common';
import { isDuplicateKey } from '@agricontract/eventing';
import type { Pool, RowDataPacket } from 'mysql2/promise';
import type { NotificationLogRepository } from '../../application/ports/notification-log.repository';
import {
  NotificationLog,
  type NotificationChannel,
  type NotificationStatus,
} from '../../domain/notification-log';
import { MYSQL_POOL } from './mysql.provider';

interface NotificationLogRow extends RowDataPacket {
  notification_id: string;
  event_id: string;
  user_id: string;
  channel: NotificationChannel;
  subject: string;
  body: string;
  status: NotificationStatus;
  retry_count: number;
}

/**
 * `notification_logs` (V1 schema, unchanged). As in the Java service the
 * `user_id` column holds the recipient email, and `(event_id, user_id)` is
 * unique. Timestamps are written by the app; the pool runs in UTC.
 */
@Injectable()
export class MysqlNotificationLogRepository implements NotificationLogRepository {
  constructor(@Inject(MYSQL_POOL) private readonly pool: Pool) {}

  async findByEventIdAndRecipient(
    eventId: string,
    recipientEmail: string,
  ): Promise<NotificationLog | null> {
    const [rows] = await this.pool.query<NotificationLogRow[]>(
      `SELECT notification_id, event_id, user_id, channel, subject, body, status, retry_count
         FROM notification_logs
        WHERE event_id = ? AND user_id = ?
        LIMIT 1`,
      [eventId, recipientEmail],
    );
    const row = rows[0];
    if (!row) return null;
    return NotificationLog.reconstitute({
      notificationId: row.notification_id,
      eventId: row.event_id,
      recipientEmail: row.user_id,
      channel: row.channel,
      subject: row.subject,
      body: row.body,
      status: row.status,
      retryCount: row.retry_count,
    });
  }

  async insert(log: NotificationLog): Promise<boolean> {
    const now = new Date();
    try {
      await this.pool.query(
        `INSERT INTO notification_logs
           (notification_id, event_id, user_id, channel, subject, body, status, retry_count, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          log.notificationId,
          log.eventId,
          log.recipientEmail,
          log.channel,
          log.subject,
          log.body,
          log.status,
          log.retryCount,
          now,
          now,
        ],
      );
      return true;
    } catch (err) {
      if (isDuplicateKey(err)) return false;
      throw err;
    }
  }

  async update(log: NotificationLog): Promise<void> {
    await this.pool.query(
      `UPDATE notification_logs
          SET status = ?, retry_count = ?, updated_at = ?
        WHERE notification_id = ?`,
      [log.status, log.retryCount, new Date(), log.notificationId],
    );
  }
}
