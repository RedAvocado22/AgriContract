import type mysql from 'mysql2/promise';
import { DatabaseDeadlineError, type DbCallOptions } from '../../application/ports.js';
import {
  persistableBody,
  type NewNotificationLog,
  type NotificationKey,
  type NotificationLog,
  type NotificationStatus,
} from '../../domain/notificationLog.js';

const ER_DUP_ENTRY = 1062;
const DEDUP_INDEX = 'uq_event_recipient_type';

// DATETIME columns carry no zone. They are written and read as UTC text so the stored value
// does not depend on the pool timezone or the process TZ.
function toUtcDatetime(date: Date): string {
  return date.toISOString().replace('T', ' ').replace('Z', '');
}

function utcColumn(column: string): string {
  return `DATE_FORMAT(${column}, '%Y-%m-%dT%H:%i:%s.%fZ') AS ${column}`;
}

interface NotificationLogRow extends mysql.RowDataPacket {
  notification_id: string;
  event_id: string;
  event_type: string;
  notification_type: string;
  user_id: string;
  recipient_email: string;
  channel: 'EMAIL';
  template_version: string;
  subject: string;
  body: string;
  status: NotificationStatus;
  retry_count: number;
  provider_message_id: string | null;
  failure_reason: string | null;
  sent_at: string | null;
  created_at: string;
  updated_at: string;
}

function isDuplicateDedupKey(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false;
  }
  const { errno, message } = error as { errno?: unknown; message?: unknown };
  return errno === ER_DUP_ENTRY && typeof message === 'string' && message.includes(DEDUP_INDEX);
}

function toNotificationLog(row: NotificationLogRow): NotificationLog {
  return {
    notificationId: row.notification_id,
    eventId: row.event_id,
    eventType: row.event_type,
    notificationType: row.notification_type,
    recipientUserId: row.user_id,
    recipientEmail: row.recipient_email,
    channel: row.channel,
    templateVersion: row.template_version,
    subject: row.subject,
    body: row.body,
    status: row.status,
    retryCount: row.retry_count,
    providerMessageId: row.provider_message_id,
    failureReason: row.failure_reason,
    sentAt: row.sent_at === null ? null : new Date(row.sent_at),
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

type SqlValue = string | number | null;

/**
 * Resolves with a pool connection, or rejects with DatabaseDeadlineError after timeoutMs.
 * A connection that arrives after the timeout goes straight back to the pool.
 */
function acquire(pool: mysql.Pool, timeoutMs: number): Promise<mysql.PoolConnection> {
  return new Promise((resolve, reject) => {
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      reject(new DatabaseDeadlineError('ACQUIRE'));
    }, timeoutMs);
    pool.getConnection().then(
      (connection) => {
        if (timedOut) {
          connection.release();
          return;
        }
        clearTimeout(timer);
        resolve(connection);
      },
      (error: unknown) => {
        if (!timedOut) {
          clearTimeout(timer);
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      },
    );
  });
}

export class NotificationLogRepository {
  constructor(
    private readonly pool: mysql.Pool,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /**
   * Runs one statement. With options.timeoutMs, the wait for a connection plus PREPARE and
   * EXECUTE are capped together; on timeout the connection is destroyed so the late result
   * cannot reach the pool. That does not cancel the statement on the server: an INSERT or
   * UPDATE may still commit after DatabaseDeadlineError was raised.
   */
  private async run<T extends mysql.QueryResult>(
    sql: string,
    values: SqlValue[],
    options: DbCallOptions | undefined,
  ): Promise<T> {
    const timeoutMs = options?.timeoutMs;
    if (timeoutMs === undefined) {
      const [result] = await this.pool.execute<T>(sql, values);
      return result;
    }
    if (timeoutMs <= 0) {
      throw new DatabaseDeadlineError('ACQUIRE');
    }

    const startedAt = Date.now();
    const connection = await acquire(this.pool, timeoutMs);
    const left = timeoutMs - (Date.now() - startedAt);
    if (left <= 0) {
      connection.release();
      throw new DatabaseDeadlineError('ACQUIRE');
    }

    const statement = connection.execute<T>(sql, values);
    let timer: NodeJS.Timeout | undefined;
    const timeout = new Promise<never>((_resolve, reject) => {
      timer = setTimeout(() => {
        reject(new DatabaseDeadlineError('QUERY'));
      }, left);
    });
    try {
      const [result] = await Promise.race([statement, timeout]);
      connection.release();
      return result;
    } catch (error) {
      if (error instanceof DatabaseDeadlineError) {
        // The statement's own settlement no longer matters; keep it from going unhandled.
        statement.catch(() => undefined);
        connection.destroy();
      } else {
        connection.release();
      }
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Inserts a PENDING row. Returns false when a row with the same dedup key already exists,
   * meaning another caller owns this notification.
   */
  async reserve(log: NewNotificationLog, options?: DbCallOptions): Promise<boolean> {
    const now = toUtcDatetime(this.now());
    try {
      await this.run(
        `INSERT INTO notification_logs
           (notification_id, event_id, event_type, notification_type, user_id, recipient_email,
            channel, template_version, subject, body, status, retry_count, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, 'EMAIL', ?, ?, ?, 'PENDING', 0, ?, ?)`,
        [
          log.notificationId,
          log.eventId,
          log.eventType,
          log.notificationType,
          log.recipientUserId,
          log.recipientEmail,
          log.templateVersion,
          log.subject,
          persistableBody(log),
          now,
          now,
        ],
        options,
      );
      return true;
    } catch (error) {
      if (isDuplicateDedupKey(error)) {
        return false;
      }
      throw error;
    }
  }

  /** Returns false when no row has this notificationId. */
  async markSent(
    notificationId: string,
    providerMessageId: string,
    sentAt: Date,
    options?: DbCallOptions,
  ): Promise<boolean> {
    const result = await this.run<mysql.ResultSetHeader>(
      `UPDATE notification_logs
          SET status = 'SENT', provider_message_id = ?, sent_at = ?, failure_reason = NULL,
              updated_at = ?
        WHERE notification_id = ?`,
      [providerMessageId, toUtcDatetime(sentAt), toUtcDatetime(this.now()), notificationId],
      options,
    );
    return result.affectedRows === 1;
  }

  /**
   * Marks the attempt failed and counts it in retry_count. failureReason must not contain
   * secrets or OTP codes. Returns false when no row has this notificationId.
   */
  async markFailed(
    notificationId: string,
    failureReason: string,
    options?: DbCallOptions,
  ): Promise<boolean> {
    const result = await this.run<mysql.ResultSetHeader>(
      `UPDATE notification_logs
          SET status = 'FAILED', failure_reason = ?, retry_count = retry_count + 1,
              updated_at = ?
        WHERE notification_id = ?`,
      [failureReason, toUtcDatetime(this.now()), notificationId],
      options,
    );
    return result.affectedRows === 1;
  }

  async findByKey(key: NotificationKey, options?: DbCallOptions): Promise<NotificationLog | null> {
    const rows = await this.run<NotificationLogRow[]>(
      `SELECT notification_id, event_id, event_type, notification_type, user_id, recipient_email,
              channel, template_version, subject, body, status, retry_count, provider_message_id,
              failure_reason, ${utcColumn('sent_at')}, ${utcColumn('created_at')},
              ${utcColumn('updated_at')}
         FROM notification_logs
        WHERE event_id = ? AND recipient_email = ? AND notification_type = ?`,
      [key.eventId, key.recipientEmail, key.notificationType],
      options,
    );
    const row = rows[0];
    return row === undefined ? null : toNotificationLog(row);
  }
}
