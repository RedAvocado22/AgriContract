import { randomUUID } from 'node:crypto';
import { OTP_NOTIFICATION_TYPE, redactedBody } from '../domain/notificationLog.js';
import { OTP_TIME_BUDGET, remainingMs, type OtpTimeBudget } from './otpTimeBudget.js';
import {
  DatabaseDeadlineError,
  EmailDeliveryError,
  type EmailSender,
  type NotificationLogStore,
  type OperationLogger,
} from './ports.js';

/** Body of POST /internal/v1/notifications/otp-email (OtpEmailRequest in the contract). */
export interface OtpEmailRequest {
  requestId: string;
  recipientUserId: string;
  recipientEmail: string;
  contractId: string;
  otpCode: string;
  signedContentHash: string;
  expiresAt: string;
}

/** Rendered in memory only; never persisted or logged because text and html hold the code. */
export interface RenderedOtpEmail {
  templateVersion: string;
  subject: string;
  text: string;
  html: string;
}

export type OtpEmailRenderer = (request: OtpEmailRequest) => RenderedOtpEmail;

export const OTP_EVENT_TYPE = 'otp.sign';

/** failure_reason when the request ran out of budget before SMTP started. */
export const DEADLINE_BEFORE_SEND = 'DEADLINE_BEFORE_SEND';

/**
 * - SENT: this request reserved the row and the provider accepted the mail.
 * - ALREADY_SENT: an earlier request with the same key was sent; nothing is sent again.
 * - PREVIOUSLY_FAILED / IN_PROGRESS: an earlier request with the same key failed or still owns
 *   the row. Nothing is sent; the caller must create a new OTP with a new requestId.
 * - NOT_SENT_DEADLINE: the request ran out of budget before SMTP started; nothing was sent.
 * - PROVIDER_FAILED: the provider cannot have accepted the mail.
 * - DELIVERY_UNKNOWN: the complete mail reached the provider but no answer came in time; it may
 *   still be delivered.
 * - RESULT_NOT_RECORDED: the provider accepted the mail but the SENT state could not be stored.
 *
 * Every outcome other than SENT and ALREADY_SENT answers 502, and the requestId never sends again.
 */
export type OtpEmailOutcome =
  | 'SENT'
  | 'ALREADY_SENT'
  | 'PREVIOUSLY_FAILED'
  | 'IN_PROGRESS'
  | 'NOT_SENT_DEADLINE'
  | 'PROVIDER_FAILED'
  | 'DELIVERY_UNKNOWN'
  | 'RESULT_NOT_RECORDED';

export interface SendOtpEmailDeps {
  store: NotificationLogStore;
  sender: EmailSender;
  render: OtpEmailRenderer;
  budget?: OtpTimeBudget;
  newId?: () => string;
  now?: () => Date;
}

export class SendOtpEmail {
  private readonly store: NotificationLogStore;
  private readonly sender: EmailSender;
  private readonly render: OtpEmailRenderer;
  private readonly budget: OtpTimeBudget;
  private readonly newId: () => string;
  private readonly now: () => Date;

  constructor(deps: SendOtpEmailDeps) {
    this.store = deps.store;
    this.sender = deps.sender;
    this.render = deps.render;
    this.budget = deps.budget ?? OTP_TIME_BUDGET;
    this.newId = deps.newId ?? randomUUID;
    this.now = deps.now ?? (() => new Date());
  }

  /**
   * Sends at most once per (requestId, recipientEmail, OTP_SIGN); never retries.
   * receivedAt is the epoch ms the HTTP request arrived; the whole call ends by
   * receivedAt + budget.requestBudgetMs.
   */
  async execute(
    request: OtpEmailRequest,
    log: OperationLogger,
    receivedAt: number,
  ): Promise<OtpEmailOutcome> {
    const deadlineAt = receivedAt + this.budget.requestBudgetMs;
    const email = this.render(request);
    const notificationId = this.newId();
    const fields = {
      requestId: request.requestId,
      recipientUserId: request.recipientUserId,
      notificationId,
      templateVersion: email.templateVersion,
    };

    // Only the request whose insert creates the row may send. A duplicate key means another
    // request owns it, whatever state it is in.
    let reserved: boolean;
    try {
      reserved = await this.store.reserve(
        {
          notificationId,
          eventId: request.requestId,
          eventType: OTP_EVENT_TYPE,
          notificationType: OTP_NOTIFICATION_TYPE,
          recipientUserId: request.recipientUserId,
          recipientEmail: request.recipientEmail,
          templateVersion: email.templateVersion,
          subject: email.subject,
          body: redactedBody(email.templateVersion),
        },
        { timeoutMs: this.dbTimeout(deadlineAt) },
      );
    } catch (error) {
      if (error instanceof DatabaseDeadlineError) {
        // The INSERT may still commit. That leaves a PENDING row nobody sends for, which
        // answers 502 to any retry: the requestId is spent either way.
        log.warn({ ...fields, phase: error.phase }, 'otp email reserve timed out; not sending');
        return 'NOT_SENT_DEADLINE';
      }
      throw error;
    }
    if (!reserved) {
      return this.existingOutcome(request, deadlineAt, log);
    }

    const left = remainingMs(deadlineAt, this.nowMs());
    if (left < this.budget.smtpBudgetMs + this.budget.postSendReserveMs) {
      log.warn({ ...fields, remainingMs: left }, 'otp email budget too low to send; not sending');
      await this.recordFailure(notificationId, DEADLINE_BEFORE_SEND, deadlineAt, fields, log);
      return 'NOT_SENT_DEADLINE';
    }

    let providerMessageId: string;
    try {
      ({ providerMessageId } = await this.sender.send(
        {
          to: request.recipientEmail,
          subject: email.subject,
          text: email.text,
          html: email.html,
        },
        {
          deadlineAt: Math.min(
            this.nowMs() + this.budget.smtpBudgetMs,
            deadlineAt - this.budget.postSendReserveMs,
          ),
        },
      ));
    } catch (error) {
      const failure =
        error instanceof EmailDeliveryError
          ? error
          : new EmailDeliveryError('SMTP_UNKNOWN_ERROR', 'UNKNOWN');
      if (failure.certainty === 'UNKNOWN') {
        log.warn(
          { ...fields, failureReason: failure.reason },
          'otp email outcome unknown; the provider may still deliver it',
        );
      }
      await this.recordFailure(notificationId, failure.reason, deadlineAt, fields, log);
      return failure.certainty === 'UNKNOWN' ? 'DELIVERY_UNKNOWN' : 'PROVIDER_FAILED';
    }

    const sentAt = this.now();
    try {
      if (
        await this.store.markSent(notificationId, providerMessageId, sentAt, {
          timeoutMs: this.dbTimeout(deadlineAt),
        })
      ) {
        log.info({ ...fields, providerMessageId, status: 'SENT' }, 'otp email sent');
        return 'SENT';
      }
      log.error({ ...fields, providerMessageId }, 'otp email sent but its row is missing');
    } catch (error) {
      // On a timeout the UPDATE may still commit, so a later lookup can find SENT.
      log.error(
        { ...fields, providerMessageId, errorCode: errorCode(error) },
        'otp email sent but SENT could not be recorded',
      );
    }
    // The mail may reach the user, yet the row may stay PENDING, so a retry with this requestId
    // gets 502 as well. Reporting 200 here would contradict what the log says.
    return 'RESULT_NOT_RECORDED';
  }

  private nowMs(): number {
    return this.now().getTime();
  }

  private dbTimeout(deadlineAt: number): number {
    return Math.min(this.budget.dbOperationTimeoutMs, remainingMs(deadlineAt, this.nowMs()));
  }

  private async existingOutcome(
    request: OtpEmailRequest,
    deadlineAt: number,
    log: OperationLogger,
  ): Promise<OtpEmailOutcome> {
    const key = {
      eventId: request.requestId,
      recipientEmail: request.recipientEmail,
      notificationType: OTP_NOTIFICATION_TYPE,
    };
    let existing;
    try {
      existing = await this.store.findByKey(key, { timeoutMs: this.dbTimeout(deadlineAt) });
    } catch (error) {
      if (error instanceof DatabaseDeadlineError) {
        log.warn(
          { requestId: request.requestId, phase: error.phase },
          'otp email lookup timed out',
        );
        return 'NOT_SENT_DEADLINE';
      }
      throw error;
    }
    const fields = {
      requestId: request.requestId,
      recipientUserId: request.recipientUserId,
      notificationId: existing?.notificationId,
      status: existing?.status,
    };
    log.info(fields, 'otp email request already handled; not sending');
    switch (existing?.status) {
      case 'SENT':
        return 'ALREADY_SENT';
      case 'FAILED':
        return 'PREVIOUSLY_FAILED';
      default:
        // PENDING, or a row that disappeared between reserve and read: either way another
        // request owns this key and nothing may be sent from here.
        return 'IN_PROGRESS';
    }
  }

  private async recordFailure(
    notificationId: string,
    reason: string,
    deadlineAt: number,
    fields: object,
    log: OperationLogger,
  ): Promise<void> {
    try {
      if (
        await this.store.markFailed(notificationId, reason, {
          timeoutMs: this.dbTimeout(deadlineAt),
        })
      ) {
        log.warn({ ...fields, failureReason: reason, status: 'FAILED' }, 'otp email failed');
        return;
      }
      log.error({ ...fields, failureReason: reason }, 'otp email failed and its row is missing');
    } catch (error) {
      // Row stays PENDING (or FAILED, if a timed-out UPDATE commits late); both answer 502.
      log.error(
        { ...fields, failureReason: reason, errorCode: errorCode(error) },
        'otp email failed and FAILED could not be recorded',
      );
    }
  }
}

/** Driver error code without the message, which may quote SQL or provider responses. */
export function errorCode(error: unknown): string {
  if (typeof error === 'object' && error !== null) {
    const { code } = error as { code?: unknown };
    if (typeof code === 'string' && /^[A-Z0-9_]{1,64}$/.test(code)) {
      return code;
    }
  }
  return 'UNKNOWN';
}
