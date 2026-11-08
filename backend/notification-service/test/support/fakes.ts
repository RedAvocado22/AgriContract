import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
  type EmailReceipt,
} from '../../src/application/ports/email.port';
import type {
  ClaimResult,
  NotificationLogRepository,
} from '../../src/application/ports/notification-log.repository';
import {
  NotificationStatus,
  type NotificationKey,
  type NotificationLog,
  type NotificationLogProps,
} from '../../src/domain/notification-log';

interface Row extends NotificationLogProps {
  /** Claim timestamp (updated_at), on the fake clock. */
  updatedAt: number;
  failureReason: string | null;
  /** sent_at, on the fake clock. */
  sentAt: number | null;
  /** provider_message_id, written with sent_at. */
  providerMessageId: string | null;
}

/** A row as stored, without the claim timestamp. */
export type StoredLog = Omit<Row, 'updatedAt'>;

/**
 * In-memory notification_logs with the MySQL repository's claim semantics
 * (schema V2): unique (event_id, recipient_email, notification_type), FAILED
 * or stale PENDING rows can be retaken, and a row from before V2 (recipient
 * `legacy:<email>`, type LEGACY_P1) that is SENT makes the claim ALREADY_SENT.
 * JavaScript runs each call to completion, so claims are atomic here too.
 */
export class InMemoryNotificationLogRepository implements NotificationLogRepository {
  private readonly rows = new Map<string, Row>();
  /** Fake clock in ms, used for the claim lease. */
  now = 0;

  private static id(key: NotificationKey) {
    return `${key.eventId}|${key.recipientEmail}|${key.notificationType}`;
  }

  claim(log: NotificationLog, leaseMs: number): Promise<ClaimResult> {
    const legacy = this.rows.get(
      InMemoryNotificationLogRepository.id(
        legacyKey(log.eventId, log.recipientEmail),
      ),
    );
    if (legacy?.status === NotificationStatus.SENT) {
      return Promise.resolve('ALREADY_SENT');
    }
    const id = InMemoryNotificationLogRepository.id(log.key);
    const row = this.rows.get(id);
    if (!row) {
      this.rows.set(id, {
        notificationId: log.notificationId,
        eventId: log.eventId,
        eventType: log.eventType,
        notificationType: log.notificationType,
        recipientUserId: log.recipientUserId,
        recipientEmail: log.recipientEmail,
        channel: log.channel,
        subject: log.subject,
        body: log.body,
        templateVersion: log.templateVersion,
        status: NotificationStatus.PENDING,
        retryCount: 0,
        updatedAt: this.now,
        failureReason: null,
        sentAt: null,
        providerMessageId: null,
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
      // As the MySQL retake does: what this attempt rendered replaces what the
      // previous one did, so the row describes the email actually sent.
      row.recipientUserId = log.recipientUserId;
      row.subject = log.subject;
      row.body = log.body;
      row.templateVersion = log.templateVersion;
      row.providerMessageId = null;
      return Promise.resolve('CLAIMED');
    }
    return Promise.resolve('IN_PROGRESS');
  }

  heartbeat(key: NotificationKey): Promise<void> {
    const row = this.rows.get(InMemoryNotificationLogRepository.id(key));
    if (row?.status === NotificationStatus.PENDING) row.updatedAt = this.now;
    return Promise.resolve();
  }

  markSent(
    key: NotificationKey,
    providerMessageId: string | null,
  ): Promise<void> {
    this.update(key, (row) => {
      row.status = NotificationStatus.SENT;
      row.sentAt = this.now;
      row.providerMessageId = providerMessageId;
    });
    return Promise.resolve();
  }

  markFailed(key: NotificationKey, failureReason: string): Promise<void> {
    this.update(key, (row) => {
      row.status = NotificationStatus.FAILED;
      row.retryCount += 1;
      row.failureReason = failureReason;
    });
    return Promise.resolve();
  }

  /** Seeds a row as another (possibly dead) delivery left it. */
  seed(
    key: NotificationKey,
    status: NotificationStatus,
    updatedAt = this.now,
  ): void {
    this.rows.set(InMemoryNotificationLogRepository.id(key), {
      notificationId: `seed-${InMemoryNotificationLogRepository.id(key)}`,
      eventId: key.eventId,
      eventType: 'seeded.event',
      notificationType: key.notificationType,
      recipientUserId: null,
      recipientEmail: key.recipientEmail,
      channel: 'EMAIL' as NotificationLogProps['channel'],
      subject: 'seeded',
      body: 'seeded',
      templateVersion: 'seeded/v1',
      status,
      retryCount: 0,
      updatedAt,
      failureReason: null,
      sentAt: status === NotificationStatus.SENT ? updatedAt : null,
      providerMessageId: null,
    });
  }

  /** Seeds a row as migration V2 left a Phase 1 row of `eventId` to `email`. */
  seedLegacy(eventId: string, email: string, status: NotificationStatus): void {
    this.seed(legacyKey(eventId, email), status);
  }

  get(key: NotificationKey): StoredLog | undefined {
    const row = this.rows.get(InMemoryNotificationLogRepository.id(key));
    if (!row) return undefined;
    const { updatedAt: _ignored, ...props } = row;
    void _ignored;
    return props;
  }

  /** Number of rows, all keys together. */
  get size(): number {
    return this.rows.size;
  }

  private update(key: NotificationKey, change: (row: Row) => void) {
    const row = this.rows.get(InMemoryNotificationLogRepository.id(key));
    if (row) {
      change(row);
      row.updatedAt = this.now;
    }
  }
}

/** Key migration V2 gave a Phase 1 row of `eventId` to `email`. */
function legacyKey(eventId: string, email: string): NotificationKey {
  return {
    eventId,
    recipientEmail: `legacy:${email}`,
    notificationType: 'LEGACY_P1',
  };
}

/**
 * Records sent mail. Recipients listed in `failFor` make send() reject the way
 * as the SMTP adapter does: with an EmailDeliveryError carrying only a code.
 * `hold()` makes the next send wait until the returned function is called.
 */
export class FakeEmailPort implements EmailPort {
  readonly sent: EmailMessage[] = [];
  readonly failFor = new Set<string>();
  /**
   * The provider id each accepted message reports, numbered over the sends
   * that succeeded: `<fake-1@provider.test>`, `<fake-2@provider.test>`...
   * Replace it with `() => null` to stand for a provider that names none.
   */
  providerMessageId: () => string | null = () =>
    `<fake-${this.sent.length}@provider.test>`;
  private held?: Promise<void>;

  hold(): () => void {
    let release!: () => void;
    this.held = new Promise<void>((resolve) => (release = resolve));
    return release;
  }

  async send(message: EmailMessage): Promise<EmailReceipt> {
    const held = this.held;
    this.held = undefined;
    if (held) await held;
    if (this.failFor.has(message.to)) {
      throw new EmailDeliveryError('EENVELOPE/550');
    }
    this.sent.push(message);
    return { providerMessageId: this.providerMessageId() };
  }
}
