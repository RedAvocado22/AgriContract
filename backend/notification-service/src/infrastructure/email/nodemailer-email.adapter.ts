import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createTransport, type Transporter } from 'nodemailer';
import type SMTPTransport from 'nodemailer/lib/smtp-transport';
import { failureCode } from '../../application/notification-errors';
import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
} from '../../application/ports/email.port';
import type { AppConfig } from '../../config/env.validation';

/** Injection token of nodemailer's `createTransport`, replaced by a fake in tests. */
export const CREATE_MAIL_TRANSPORT = Symbol('CREATE_MAIL_TRANSPORT');
export type CreateMailTransport = (
  options: SMTPTransport.Options,
) => Pick<Transporter, 'sendMail'>;

/**
 * SMTP sender with the Java settings (application.yml): plain SMTP on
 * MAIL_HOST:MAIL_PORT, no auth, no STARTTLS, text body, from MAIL_FROM.
 * Unlike JavaMail's unlimited defaults, timeouts are bounded so a hung server
 * cannot hold a consumer (and its prefetch slot) forever; the failure is
 * then retried like any other send error.
 */
@Injectable()
export class NodemailerEmailAdapter implements EmailPort {
  private readonly transport: Pick<Transporter, 'sendMail'>;
  private readonly from: string;

  constructor(
    config: ConfigService<AppConfig, true>,
    @Inject(CREATE_MAIL_TRANSPORT) create: CreateMailTransport,
  ) {
    const mail = config.get('mail', { infer: true });
    this.from = mail.from;
    this.transport = create({
      host: mail.host,
      port: mail.port,
      secure: false,
      ignoreTLS: true,
      auth: undefined,
      connectionTimeout: 10_000,
      greetingTimeout: 10_000,
      socketTimeout: 30_000,
    });
  }

  async send({ to, subject, body }: EmailMessage): Promise<void> {
    try {
      await this.transport.sendMail({
        from: this.from,
        to,
        subject,
        text: body,
      });
    } catch (err) {
      // Only a code leaves this adapter: nodemailer texts quote recipient
      // addresses ("550 5.1.1 <x@y>: Recipient address rejected"). No cause is
      // attached either, so nothing downstream can log the original by accident.
      throw new EmailDeliveryError(smtpFailureCode(err));
    }
  }
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
