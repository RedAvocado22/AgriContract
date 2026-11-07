import {
  NotificationChannel,
  NotificationLog,
} from '../../domain/notification-log';
import { CLAIM_LEASE_MS, type SenderLogger } from '../notification-sender';
import { NotificationError, failureCode } from '../notification-errors';
import { EmailDeliveryError, type EmailPort } from '../ports/email.port';
import type { NotificationLogRepository } from '../ports/notification-log.repository';
import type { OtpEmailRequest } from './otp-email-request';
import {
  OTP_EMAIL_KIND,
  OTP_EMAIL_LOG_BODY,
  renderOtpEmail,
} from './otp-email-template';

/*
 * Errors carry the requestId (the caller's otpId) and a failure code only:
 * never the address, the OTP or a provider/driver text.
 */

/** 502: the mail server did not accept the OTP email. */
export class OtpEmailDeliveryError extends NotificationError {
  constructor(
    readonly requestId: string,
    readonly code: string,
  ) {
    super(`OTP request ${requestId}: email delivery failed (${code})`);
  }
}

/** 503: another call with the same requestId is sending this email right now. */
export class OtpEmailInProgressError extends NotificationError {
  constructor(readonly requestId: string) {
    super(`OTP request ${requestId}: another call is sending this email`);
  }
}

/** 503: notification_logs could not be read or written before sending. */
export class OtpEmailLogUnavailableError extends NotificationError {
  constructor(
    readonly requestId: string,
    readonly code: string,
  ) {
    super(`OTP request ${requestId}: notification log unavailable (${code})`);
  }
}

/** SENT: this call sent the email. ALREADY_SENT: an earlier call with the same requestId did. */
export type OtpEmailResult = 'SENT' | 'ALREADY_SENT';

/**
 * Sends the OTP email for contract signing synchronously (design §2.2, §5).
 *
 * A call is claimed in notification_logs by (requestId, recipientEmail,
 * CONTRACT_SIGNING_OTP) before sending, so a repeated call never sends twice.
 * Since V2 (#115) the address is in recipient_email (and still in user_id, as
 * in every Phase 1 row and in Java). recipientUserId is not stored yet: V2
 * adds recipient_user_id as a nullable column, and the Phase 2 recipients[]
 * work fills it.
 * - no row, or the earlier call FAILED: send now (the caller asked again, so
 *   this is not a late background send);
 * - SENT: return ALREADY_SENT without sending;
 * - another call holds a live PENDING claim: OtpEmailInProgressError at once.
 *   Unlike the queue consumers there is no claim wait: the caller's read
 *   timeout (≥ 5 s, #167) is far below it.
 *
 * Nothing is retried in the background after a failure is returned
 * (design §5). The stored body is a placeholder: the OTP is only in memory.
 */
export class SendOtpEmailUseCase {
  constructor(
    private readonly logs: NotificationLogRepository,
    private readonly email: EmailPort,
    private readonly logger: SenderLogger,
  ) {}

  async execute(request: OtpEmailRequest): Promise<OtpEmailResult> {
    const { requestId, recipientEmail } = request;
    const { subject, body } = renderOtpEmail(request);
    const log = NotificationLog.create(
      requestId,
      OTP_EMAIL_KIND,
      recipientEmail,
      NotificationChannel.EMAIL,
      subject,
      OTP_EMAIL_LOG_BODY,
    );

    let claim;
    try {
      claim = await this.logs.claim(log, CLAIM_LEASE_MS);
    } catch (err) {
      throw new OtpEmailLogUnavailableError(requestId, failureCode(err));
    }
    if (claim === 'ALREADY_SENT') return 'ALREADY_SENT';
    if (claim === 'IN_PROGRESS') throw new OtpEmailInProgressError(requestId);

    try {
      await this.email.send({ to: recipientEmail, subject, body });
    } catch (err) {
      const code =
        err instanceof EmailDeliveryError ? err.code : failureCode(err);
      await this.logs
        .markFailed(log.key, code)
        .catch((logErr: unknown) =>
          this.logger.error(
            `OTP request ${requestId}: could not record FAILED (${failureCode(logErr)})`,
          ),
        );
      throw new OtpEmailDeliveryError(requestId, code);
    }

    // The provider accepted the email, so the call succeeds even if this
    // write fails. The row then stays PENDING: a repeated call gets 503 until
    // the lease ends, then sends again (a duplicate, never a lost email).
    await this.logs
      .markSent(log.key)
      .catch((err: unknown) =>
        this.logger.error(
          `OTP request ${requestId}: email sent but could not record SENT (${failureCode(err)})`,
        ),
      );
    return 'SENT';
  }
}
