import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createTransport, type Transporter } from 'nodemailer';
import type SMTPTransport from 'nodemailer/lib/smtp-transport';
import type {
  EmailMessage,
  EmailPort,
} from '../../application/ports/email.port';
import type { AppConfig } from '../../config/env.validation';
import { describeError } from '../describe-error';

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
  private readonly server: string;

  constructor(
    config: ConfigService<AppConfig, true>,
    @Inject(CREATE_MAIL_TRANSPORT) create: CreateMailTransport,
  ) {
    const mail = config.get('mail', { infer: true });
    this.from = mail.from;
    this.server = `${mail.host}:${mail.port}`;
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
      // Connection errors to "localhost" come as an AggregateError with an empty message.
      throw new Error(`SMTP ${this.server}: ${describeError(err)}`, {
        cause: err,
      });
    }
  }
}

export const mailTransportFactory = {
  provide: CREATE_MAIL_TRANSPORT,
  useValue: ((options: SMTPTransport.Options) =>
    createTransport(options)) satisfies CreateMailTransport,
};
