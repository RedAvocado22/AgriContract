import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
} from '../../src/application/ports/email.port';
import type {
  ClaimResult,
  NotificationLogRepository,
} from '../../src/application/ports/notification-log.repository';
import {
  NotificationStatus,
  type NotificationLog,
  type NotificationLogProps,
} from '../../src/domain/notification-log';

interface Row extends NotificationLogProps {
  /** Claim timestamp (updated_at), on the fake clock. */
  updatedAt: number;
}

/**
 * In-memory notification_logs with the MySQL repository's claim semantics:
 * unique (event_id, user_id), FAILED or stale PENDING rows can be retaken.
 * JavaScript runs each call to completion, so claims are atomic here too.
 */
export class InMemoryNotificationLogRepository implements NotificationLogRepository {
  private readonly rows = new Map<string, Row>();
  /** Fake clock in ms, used for the claim lease. */
  now = 0;

  private static key(eventId: string, recipient: string) {
    return `${eventId}|${recipient}`;
  }

  claim(log: NotificationLog, leaseMs: number): Promise<ClaimResult> {
    const key = InMemoryNotificationLogRepository.key(
      log.eventId,
      log.recipientEmail,
    );
    const row = this.rows.get(key);
    if (!row) {
      this.rows.set(key, {
        notificationId: log.notificationId,
        eventId: log.eventId,
        recipientEmail: log.recipientEmail,
        channel: log.channel,
        subject: log.subject,
        body: log.body,
        status: NotificationStatus.PENDING,
        retryCount: 0,
        updatedAt: this.now,
      });
      return Promise.resolve('CLAIMED');
    }
    if (row.status === NotificationStatus.SENT) {
      return Promise.resolve('ALREADY_SENT');
    }
    const stale = row.updatedAt < this.now - leaseMs;
    if (row.status === NotificationStatus.FAILED || stale) {
      row.status = NotificationStatus.PENDING;
      row.updatedAt = this.now;
      return Promise.resolve('CLAIMED');
    }
    return Promise.resolve('IN_PROGRESS');
  }

  heartbeat(eventId: string, recipientEmail: string): Promise<void> {
    const row = this.rows.get(
      InMemoryNotificationLogRepository.key(eventId, recipientEmail),
    );
    if (row?.status === NotificationStatus.PENDING) row.updatedAt = this.now;
    return Promise.resolve();
  }

  markSent(eventId: string, recipientEmail: string): Promise<void> {
    this.update(eventId, recipientEmail, (row) => {
      row.status = NotificationStatus.SENT;
    });
    return Promise.resolve();
  }

  markFailed(eventId: string, recipientEmail: string): Promise<void> {
    this.update(eventId, recipientEmail, (row) => {
      row.status = NotificationStatus.FAILED;
      row.retryCount += 1;
    });
    return Promise.resolve();
  }

  /** Seeds a row as another (possibly dead) delivery left it. */
  seed(
    eventId: string,
    recipient: string,
    status: NotificationStatus,
    updatedAt = this.now,
  ): void {
    this.rows.set(InMemoryNotificationLogRepository.key(eventId, recipient), {
      notificationId: `seed-${eventId}-${recipient}`,
      eventId,
      recipientEmail: recipient,
      channel: 'EMAIL' as NotificationLogProps['channel'],
      subject: 'seeded',
      body: 'seeded',
      status,
      retryCount: 0,
      updatedAt,
    });
  }

  get(eventId: string, recipient: string): NotificationLogProps | undefined {
    const row = this.rows.get(
      InMemoryNotificationLogRepository.key(eventId, recipient),
    );
    if (!row) return undefined;
    const { updatedAt: _ignored, ...props } = row;
    void _ignored;
    return props;
  }

  private update(
    eventId: string,
    recipient: string,
    change: (row: Row) => void,
  ) {
    const row = this.rows.get(
      InMemoryNotificationLogRepository.key(eventId, recipient),
    );
    if (row) {
      change(row);
      row.updatedAt = this.now;
    }
  }
}

/**
 * Records sent mail. Recipients listed in `failFor` make send() reject the way
 * as the SMTP adapter does: with an EmailDeliveryError carrying only a code.
 * `hold()` makes the next send wait until the returned function is called.
 */
export class FakeEmailPort implements EmailPort {
  readonly sent: EmailMessage[] = [];
  readonly failFor = new Set<string>();
  private held?: Promise<void>;

  hold(): () => void {
    let release!: () => void;
    this.held = new Promise<void>((resolve) => (release = resolve));
    return release;
  }

  async send(message: EmailMessage): Promise<void> {
    const held = this.held;
    this.held = undefined;
    if (held) await held;
    if (this.failFor.has(message.to)) {
      throw new EmailDeliveryError('EENVELOPE/550');
    }
    this.sent.push(message);
  }
}
