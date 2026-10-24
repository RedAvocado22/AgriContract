import {
  NotificationChannel,
  NotificationLog,
} from '../domain/notification-log';
import {
  NotificationDeliveryError,
  SendInProgressError,
  failureCode,
} from './notification-errors';
import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
} from './ports/email.port';
import type { NotificationLogRepository } from './ports/notification-log.repository';

/*
 * Claim timing (plan §4.3). A PENDING row is a claim on one email; its
 * updated_at (database clock) is refreshed by a heartbeat while the email is
 * being sent, so only the claim of a delivery that died mid-send ages past
 * the lease and can be taken over.
 */

/** A PENDING claim not refreshed for this long is dead: above the 30s SMTP socket timeout. */
export const CLAIM_LEASE_MS = 45_000;

/** How often a live claim is refreshed while sending: three beats per lease. */
export const CLAIM_HEARTBEAT_MS = 15_000;

/**
 * How long a delivery waits for another delivery's live claim to end before
 * giving up with a retryable error: above the lease, so the claim of a
 * delivery that died is always taken over within the wait.
 */
export const CLAIM_WAIT_MS = 50_000;

/** How often the waiting delivery looks at the row again. */
export const CLAIM_POLL_MS = 1_000;

/** Where the sender reports failures it does not throw (heartbeats). */
export interface SenderLogger {
  error(message: string): void;
}

export interface NotificationSenderOptions {
  logger?: SenderLogger;
  /** Monotonic clock in ms for the claim wait; replaced in tests. */
  now?: () => number;
  /** Pause between two looks at a claimed row; replaced in tests. */
  sleep?: (ms: number) => Promise<void>;
}

/**
 * Sends one email for one event to one recipient, at most once (plan §4.3).
 * Shared by the Notify*UseCase classes; plain TypeScript, no framework.
 */
export class NotificationSender {
  private readonly logger: SenderLogger;
  private readonly now: () => number;
  private readonly sleep: (ms: number) => Promise<void>;

  constructor(
    private readonly logs: NotificationLogRepository,
    private readonly email: EmailPort,
    options: NotificationSenderOptions = {},
  ) {
    this.logger = options.logger ?? console;
    this.now = options.now ?? (() => performance.now());
    this.sleep =
      options.sleep ??
      ((ms) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  }

  /**
   * 1. Claim the send atomically (insert PENDING, or retake a FAILED row or a
   *    PENDING row older than the lease). ALREADY_SENT: done.
   * 2. While another delivery of the same event holds a live claim, look
   *    again every CLAIM_POLL_MS for up to CLAIM_WAIT_MS: SENT means done,
   *    FAILED or an expired claim is taken over and sent. Still claimed after
   *    the wait: throw a retryable error, so the message comes back after the
   *    backoff. Acking instead would lose the email if the other delivery
   *    then fails. The message stays unacked while waiting.
   * 3. Send outside any transaction, refreshing the claim every
   *    CLAIM_HEARTBEAT_MS until the send ends, then record SENT, or FAILED
   *    with retry_count + 1 and rethrow so the whole message is retried
   *    (recipients already SENT are skipped then).
   *
   * Every error thrown is a NotificationError whose message holds only the
   * event id and a failure code: it is logged on each retry, so it must not
   * quote the recipient address or provider/database texts (review #172).
   */
  async send(
    eventId: string,
    recipientEmail: string,
    subject: string,
    body: string,
  ): Promise<void> {
    const claim = await this.claimOrWait(
      NotificationLog.create(
        eventId,
        recipientEmail,
        NotificationChannel.EMAIL,
        subject,
        body,
      ),
    );
    if (claim === 'ALREADY_SENT') return;

    try {
      await this.sendWithHeartbeat(eventId, {
        to: recipientEmail,
        subject,
        body,
      });
    } catch (err) {
      await this.logStep(eventId, () =>
        this.logs.markFailed(eventId, recipientEmail),
      );
      const code =
        err instanceof EmailDeliveryError ? err.code : failureCode(err);
      throw new NotificationDeliveryError(eventId, 'email', code);
    }
    await this.logStep(eventId, () =>
      this.logs.markSent(eventId, recipientEmail),
    );
  }

  /**
   * Claims the send, polling while another delivery holds a live claim
   * (step 2 of send). Each look is a full claim attempt, so taking over a
   * FAILED or expired row stays atomic between concurrent waiters.
   */
  private async claimOrWait(
    log: NotificationLog,
  ): Promise<'CLAIMED' | 'ALREADY_SENT'> {
    const deadline = this.now() + CLAIM_WAIT_MS;
    for (;;) {
      const claim = await this.logStep(log.eventId, () =>
        this.logs.claim(log, CLAIM_LEASE_MS),
      );
      if (claim !== 'IN_PROGRESS') return claim;
      if (this.now() >= deadline) throw new SendInProgressError(log.eventId);
      await this.sleep(CLAIM_POLL_MS);
    }
  }

  /**
   * Sends `message`, refreshing its claim every CLAIM_HEARTBEAT_MS until the
   * send ends, successfully or not. A failed beat is logged (event id and
   * code only) and does not stop the send: the next beat may succeed, and if
   * none does the claim can be taken over after the lease, as for a crash.
   */
  private async sendWithHeartbeat(
    eventId: string,
    message: EmailMessage,
  ): Promise<void> {
    let beating = false;
    const timer = setInterval(() => {
      if (beating) return; // the previous beat is still waiting for the database
      beating = true;
      this.logs
        .heartbeat(eventId, message.to)
        .catch((err: unknown) =>
          this.logger.error(
            `Event ${eventId}: claim heartbeat failed (${failureCode(err)})`,
          ),
        )
        .finally(() => (beating = false));
    }, CLAIM_HEARTBEAT_MS);
    try {
      await this.email.send(message);
    } finally {
      clearInterval(timer);
    }
  }

  /** Runs a repository call; a failure becomes a code-only error (driver texts may quote values). */
  private async logStep<T>(
    eventId: string,
    step: () => Promise<T>,
  ): Promise<T> {
    try {
      return await step();
    } catch (err) {
      throw new NotificationDeliveryError(eventId, 'log', failureCode(err));
    }
  }
}
