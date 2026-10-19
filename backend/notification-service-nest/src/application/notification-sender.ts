import {
  NotificationChannel,
  NotificationLog,
} from '../domain/notification-log';
import type { EmailPort } from './ports/email.port';
import type { NotificationLogRepository } from './ports/notification-log.repository';

/**
 * How long a PENDING claim protects a send in progress. Well above the
 * longest possible SMTP attempt (10s connect + 10s greeting + 30s socket), so
 * a live claim is never taken over; after it, the claim of a delivery that
 * died mid-send can be taken again.
 */
export const CLAIM_LEASE_MS = 120_000;

/**
 * Sends one email for one event to one recipient, at most once (plan §4.3).
 * Shared by the Notify*UseCase classes; plain TypeScript, no framework.
 */
export class NotificationSender {
  constructor(
    private readonly logs: NotificationLogRepository,
    private readonly email: EmailPort,
  ) {}

  /**
   * 1. Claim the send atomically (insert PENDING, or retake a FAILED / stale
   *    PENDING row). ALREADY_SENT: done.
   * 2. IN_PROGRESS: another delivery of the same event holds a live claim;
   *    throw a retryable error so this message comes back after the backoff,
   *    by when the other delivery has usually recorded SENT. Acking instead
   *    would lose the email if that other delivery then fails.
   * 3. Send outside any transaction, then record SENT, or FAILED with
   *    retry_count + 1 and rethrow so the whole message is retried
   *    (recipients already SENT are skipped then).
   */
  async send(
    eventId: string,
    recipientEmail: string,
    subject: string,
    body: string,
  ): Promise<void> {
    const claim = await this.logs.claim(
      NotificationLog.create(
        eventId,
        recipientEmail,
        NotificationChannel.EMAIL,
        subject,
        body,
      ),
      CLAIM_LEASE_MS,
    );
    if (claim === 'ALREADY_SENT') return;
    if (claim === 'IN_PROGRESS') {
      throw new Error(
        `Event ${eventId}: another delivery is sending this email; retrying later`,
      );
    }

    try {
      await this.email.send({ to: recipientEmail, subject, body });
    } catch (err) {
      await this.logs.markFailed(eventId, recipientEmail);
      throw new Error(
        `Sending "${subject}" to ${recipientEmail} for event ${eventId} failed: ${
          err instanceof Error ? err.message : JSON.stringify(err)
        }`,
        { cause: err },
      );
    }
    await this.logs.markSent(eventId, recipientEmail);
  }
}
