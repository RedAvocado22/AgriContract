import { Inject, Injectable } from '@nestjs/common';
import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from '../domain/notification-log';
import { EMAIL_PORT, type EmailPort } from './ports/email.port';
import {
  NOTIFICATION_LOG_REPOSITORY,
  type NotificationLogRepository,
} from './ports/notification-log.repository';

/** Port of the Java ProcessNotificationUseCaseImpl. */
@Injectable()
export class ProcessNotificationService {
  constructor(
    @Inject(NOTIFICATION_LOG_REPOSITORY)
    private readonly logs: NotificationLogRepository,
    @Inject(EMAIL_PORT) private readonly email: EmailPort,
  ) {}

  /**
   * Sends one email for one event to one recipient, at most once (plan §4.3,
   * same steps as the Java sendNotification):
   * 1. a log already SENT for (eventId, recipient) means done;
   * 2. otherwise insert a PENDING log; losing that insert to another delivery
   *    of the same event means that delivery owns the send, so stop;
   *    an existing PENDING or FAILED log is sent again;
   * 3. send, then save SENT, or FAILED with retry_count + 1 and rethrow so the
   *    whole message is retried (recipients already SENT are skipped then).
   */
  async sendNotification(
    eventId: string,
    recipientEmail: string,
    subject: string,
    body: string,
  ): Promise<void> {
    let log = await this.logs.findByEventIdAndRecipient(
      eventId,
      recipientEmail,
    );
    if (log?.status === NotificationStatus.SENT) return;

    if (!log) {
      log = NotificationLog.create(
        eventId,
        recipientEmail,
        NotificationChannel.EMAIL,
        subject,
        body,
      );
      if (!(await this.logs.insert(log))) return;
    }

    let failure: unknown;
    try {
      await this.email.send({ to: recipientEmail, subject, body });
      log.markSent();
    } catch (err) {
      log.markFailed();
      failure = err;
    }
    await this.logs.update(log);
    if (failure !== undefined) {
      throw new Error(
        `Sending "${subject}" to ${recipientEmail} for event ${eventId} failed: ${
          failure instanceof Error ? failure.message : JSON.stringify(failure)
        }`,
        { cause: failure },
      );
    }
  }
}
