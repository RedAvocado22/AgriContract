import type {
  EmailMessage,
  EmailPort,
} from '../../src/application/ports/email.port';
import type { NotificationLogRepository } from '../../src/application/ports/notification-log.repository';
import {
  NotificationLog,
  type NotificationLogProps,
} from '../../src/domain/notification-log';

/** In-memory notification_logs with the same unique key (event_id, user_id). */
export class InMemoryNotificationLogRepository implements NotificationLogRepository {
  readonly rows = new Map<string, NotificationLogProps>();
  /** Makes the next insert lose a race, as if another consumer inserted first. */
  loseNextInsertRace = false;

  private static key(eventId: string, recipient: string) {
    return `${eventId}|${recipient}`;
  }

  findByEventIdAndRecipient(eventId: string, recipientEmail: string) {
    const row = this.rows.get(
      InMemoryNotificationLogRepository.key(eventId, recipientEmail),
    );
    return Promise.resolve(row ? NotificationLog.reconstitute(row) : null);
  }

  insert(log: NotificationLog) {
    const key = InMemoryNotificationLogRepository.key(
      log.eventId,
      log.recipientEmail,
    );
    if (this.loseNextInsertRace) {
      this.loseNextInsertRace = false;
      return Promise.resolve(false);
    }
    if (this.rows.has(key)) return Promise.resolve(false);
    this.rows.set(key, snapshot(log));
    return Promise.resolve(true);
  }

  update(log: NotificationLog) {
    this.rows.set(
      InMemoryNotificationLogRepository.key(log.eventId, log.recipientEmail),
      snapshot(log),
    );
    return Promise.resolve();
  }

  get(eventId: string, recipient: string): NotificationLogProps | undefined {
    return this.rows.get(
      InMemoryNotificationLogRepository.key(eventId, recipient),
    );
  }
}

function snapshot(log: NotificationLog): NotificationLogProps {
  return {
    notificationId: log.notificationId,
    eventId: log.eventId,
    recipientEmail: log.recipientEmail,
    channel: log.channel,
    subject: log.subject,
    body: log.body,
    status: log.status,
    retryCount: log.retryCount,
  };
}

/** Records sent mail; recipients listed in `failFor` make send() reject. */
export class FakeEmailPort implements EmailPort {
  readonly sent: EmailMessage[] = [];
  readonly failFor = new Set<string>();

  send(message: EmailMessage): Promise<void> {
    if (this.failFor.has(message.to)) {
      return Promise.reject(new Error(`SMTP refused ${message.to}`));
    }
    this.sent.push(message);
    return Promise.resolve();
  }
}
