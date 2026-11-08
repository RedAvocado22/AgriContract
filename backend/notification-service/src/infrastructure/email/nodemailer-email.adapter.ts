import { Inject, Injectable, Optional } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createTransport, type Transporter } from 'nodemailer';
import type SMTPTransport from 'nodemailer/lib/smtp-transport';
import { failureCode } from '../../application/notification-errors';
import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
  type EmailReceipt,
} from '../../application/ports/email.port';
import type { AppConfig } from '../../config/env.validation';

/** Injection token of nodemailer's `createTransport`, replaced by a fake in tests. */
export const CREATE_MAIL_TRANSPORT = Symbol('CREATE_MAIL_TRANSPORT');
export type CreateMailTransport = (
  options: SMTPTransport.Options,
) => Pick<Transporter, 'sendMail'>;

/** Optional injection token of the SmtpTimeouts an adapter uses. */
export const SMTP_TIMEOUTS = Symbol('SMTP_TIMEOUTS');

export interface SmtpTimeouts {
  connectionTimeout: number;
  greetingTimeout: number;
  socketTimeout: number;
}

/** Queue consumers: a hung server cannot hold a consumer (and its prefetch slot) forever. */
export const DEFAULT_SMTP_TIMEOUTS: SmtpTimeouts = {
  connectionTimeout: 10_000,
  greetingTimeout: 10_000,
  socketTimeout: 30_000,
};

/**
 * OTP endpoint: fail (502) before the caller's read timeout of at least 5 s
 * (#167), instead of sending after the caller already reported a failure.
 * socketTimeout is an inactivity timeout, so a server that keeps answering
 * slowly can still exceed the total; see the README's known limitations.
 */
export const OTP_SMTP_TIMEOUTS: SmtpTimeouts = {
  connectionTimeout: 1_000,
  greetingTimeout: 1_000,
  socketTimeout: 2_500,
};

/**
 * SMTP sender with the Java settings (application.yml): plain SMTP on
 * MAIL_HOST:MAIL_PORT, no auth, no STARTTLS, text body, from MAIL_FROM.
 * Unlike JavaMail's unlimited defaults, timeouts are bounded
 * (DEFAULT_SMTP_TIMEOUTS unless given); a failure is then handled like any
 * other send error.
 */
@Injectable()
export class NodemailerEmailAdapter implements EmailPort {
  private readonly transport: Pick<Transporter, 'sendMail'>;
  private readonly from: string;

  constructor(
    config: ConfigService<AppConfig, true>,
    @Inject(CREATE_MAIL_TRANSPORT) create: CreateMailTransport,
    @Optional()
    @Inject(SMTP_TIMEOUTS)
    timeouts: SmtpTimeouts = DEFAULT_SMTP_TIMEOUTS,
  ) {
    const mail = config.get('mail', { infer: true });
    this.from = mail.from;
    this.transport = create({
      host: mail.host,
      port: mail.port,
      secure: false,
      ignoreTLS: true,
      auth: undefined,
      ...timeouts,
    });
  }

  async send({ to, subject, body }: EmailMessage): Promise<EmailReceipt> {
    try {
      const info = await this.transport.sendMail({
        from: this.from,
        to,
        subject,
        text: body,
      });
      return { providerMessageId: sentMessageId(info) };
    } catch (err) {
      // Only a code leaves this adapter: nodemailer texts quote recipient
      // addresses ("550 5.1.1 <x@y>: Recipient address rejected"). No cause is
      // attached either, so nothing downstream can log the original by accident.
      throw new EmailDeliveryError(smtpFailureCode(err));
    }
  }
}

/**
 * The Message-ID the server accepted (`provider_message_id`, design §8): the
 * id a bounce quotes when it answers this mail. A transport that names none,
 * or names a blank one, gives null rather than an empty string, so the column
 * reads "unknown" instead of "".
 *
 * Reads nothing else of the info object and never throws, on purpose: this
 * runs after the provider already accepted the email, and an error here would
 * be caught as a delivery failure and send the mail a second time.
 */
export function sentMessageId(
  info: { messageId?: string } | null | undefined,
): string | null {
  const id = info?.messageId?.trim() ?? '';
  return id.length > 0 ? id : null;
}

/**
 * `ECONNREFUSED`, `ETIMEDOUT`, `EENVELOPE/550`...: nodemailer's error code plus
 * the SMTP reply code when there is one. Connecting to "localhost" fails with
 * an AggregateError (::1 and 127.0.0.1); its first inner error names the cause.
 */
export function smtpFailureCode(err: unknown): string {
  if (err instanceof AggregateError && err.errors.length > 0) {
    return smtpFailureCode(err.errors[0]);
  }
  const code = failureCode(err);
  const reply = (err as { responseCode?: unknown } | null)?.responseCode;
  return typeof reply === 'number' && Number.isInteger(reply)
    ? `${code}/${reply}`
    : code;
}

export const mailTransportFactory = {
  provide: CREATE_MAIL_TRANSPORT,
  useValue: ((options: SMTPTransport.Options) =>
    createTransport(options)) satisfies CreateMailTransport,
};
