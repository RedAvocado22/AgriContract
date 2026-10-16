import { Readable } from 'node:stream';
import MailComposer from 'nodemailer/lib/mail-composer';
import SMTPConnection from 'nodemailer/lib/smtp-connection';
import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailSendOptions,
  type EmailSendResult,
  type EmailSender,
} from '../../application/ports.js';

/** Same default sender as the Java EmailPortAdapter (spring.mail.from). */
export const DEFAULT_MAIL_FROM = 'noreply@agricontract.com';

export interface SmtpTimeouts {
  /** DNS lookup, TCP connect and greeting together. */
  connectionTimeout: number;
  greetingTimeout: number;
  /** Inactivity limit per read/write; it does not cap the total time on its own. */
  socketTimeout: number;
  /** Hard cap on the whole send. Calls may pass an earlier deadline. */
  totalTimeout: number;
  /**
   * Time that must still be left when the server answers DATA with 354 for the message body
   * to be written. Below it the body is withheld, so the server never sees the terminating
   * "." and cannot accept the message.
   */
  dataResponseReserve: number;
}

/**
 * Part of OTP_TIME_BUDGET (application/otpTimeBudget.ts): SMTP gets at most 2500 ms of the
 * 4500 ms request budget. The body is only written with at least 1000 ms left for the reply.
 */
export const OTP_SMTP_TIMEOUTS: SmtpTimeouts = {
  connectionTimeout: 1000,
  greetingTimeout: 1000,
  socketTimeout: 1000,
  totalTimeout: 2500,
  dataResponseReserve: 1000,
};

export interface SmtpSettings {
  host: string;
  port: number;
  from: string;
}

const MAX_PROVIDER_MESSAGE_ID = 255;

/** Raised inside the body stream when too little time is left to write it. */
class BodyWithheld extends Error {}

/** reason from codes only; provider messages can echo addresses or message content. */
function codeReason(error: unknown): string {
  const { code, responseCode } = (typeof error === 'object' && error !== null ? error : {}) as {
    code?: unknown;
    responseCode?: unknown;
  };
  const safeCode = typeof code === 'string' && /^E[A-Z0-9]{1,30}$/.test(code) ? code : 'ERROR';
  const suffix = typeof responseCode === 'number' ? `_${String(responseCode)}` : '';
  return `SMTP_${safeCode}${suffix}`;
}

/** A 4xx/5xx reply means the server answered, and refused. */
function hasRejectionReply(error: unknown): boolean {
  const { responseCode } = (typeof error === 'object' && error !== null ? error : {}) as {
    responseCode?: unknown;
  };
  return typeof responseCode === 'number' && responseCode >= 400;
}

/** Provider queue id from "250 Ok: queued as <id>", else the Message-ID header we set. */
function providerMessageId(response: string, messageId: string): string {
  const queued = /queued as (\S+)/i.exec(response)?.[1];
  return (queued ?? messageId).slice(0, MAX_PROVIDER_MESSAGE_ID);
}

/**
 * One SMTP connection per message, driven directly so the deadline can close it.
 *
 * Nodemailer pipes the message only after the server answered DATA with 354, so the body is a
 * stream that checks the deadline at that moment and either writes the whole message (and the
 * terminating ".") or nothing. Once written, the server may accept it at any time; a failure
 * after that point is reported as UNKNOWN, never as NOT_SENT. Closing the socket does not
 * retract a message the server already received.
 */
export class SmtpEmailSender implements EmailSender {
  constructor(
    private readonly settings: SmtpSettings,
    private readonly timeouts: SmtpTimeouts = OTP_SMTP_TIMEOUTS,
  ) {}

  async send(message: EmailMessage, options: EmailSendOptions): Promise<EmailSendResult> {
    const endAt = Math.min(Date.now() + this.timeouts.totalTimeout, options.deadlineAt);
    if (endAt - Date.now() < this.timeouts.dataResponseReserve) {
      throw new EmailDeliveryError('SMTP_DEADLINE_BEFORE_CONNECT');
    }
    const mail = new MailComposer({
      from: this.settings.from,
      to: message.to,
      subject: message.subject,
      text: message.text,
      html: message.html,
    }).compile();
    const messageId = mail.messageId();
    const raw = await mail.build();
    const response = await this.deliver(mail.getEnvelope(), raw, endAt);
    return { providerMessageId: providerMessageId(response, messageId) };
  }

  private deliver(
    envelope: { from: string | false; to: string[] },
    raw: Buffer,
    endAt: number,
  ): Promise<string> {
    // Mirrors the Java service: no auth, no STARTTLS (MailHog advertises neither).
    const connection = new SMTPConnection({
      host: this.settings.host,
      port: this.settings.port,
      secure: false,
      ignoreTLS: true,
      logger: false,
      connectionTimeout: this.timeouts.connectionTimeout,
      greetingTimeout: this.timeouts.greetingTimeout,
      socketTimeout: this.timeouts.socketTimeout,
    });
    const reserve = this.timeouts.dataResponseReserve;
    // True once the whole message, terminating "." included, was handed to the socket.
    let committed = false;
    let withheld = false;

    const body = new Readable({
      read() {
        if (endAt - Date.now() < reserve) {
          withheld = true;
          this.destroy(new BodyWithheld());
          return;
        }
        committed = true;
        this.push(raw);
        this.push(null);
      },
    });

    return new Promise<string>((resolve, reject) => {
      let settled = false;
      const fail = (reason: string, error?: unknown): void => {
        if (settled) {
          return;
        }
        // After the body is out, only an explicit rejection proves the server did not take it.
        const unknown = committed && !hasRejectionReply(error);
        settled = true;
        clearTimeout(deadline);
        body.destroy();
        connection.close();
        reject(
          new EmailDeliveryError(
            unknown ? `${reason}_AFTER_DATA` : reason,
            unknown ? 'UNKNOWN' : 'NOT_SENT',
          ),
        );
      };
      const succeed = (response: string): void => {
        if (settled) {
          return;
        }
        settled = true;
        clearTimeout(deadline);
        connection.quit();
        resolve(response);
      };

      const deadline = setTimeout(
        () => {
          fail('SMTP_DEADLINE_EXCEEDED');
        },
        Math.max(0, endAt - Date.now()),
      );

      // Stays attached after settling so late socket errors are not unhandled. A late reply
      // after close() is dropped by Nodemailer, which clears its pending response handlers.
      connection.on('error', (error: unknown) => {
        fail(codeReason(error), error);
      });
      connection.once('end', () => {
        fail('SMTP_CONNECTION_CLOSED');
      });
      connection.connect((connectError) => {
        if (connectError !== undefined) {
          fail(codeReason(connectError), connectError);
          return;
        }
        if (endAt - Date.now() < reserve) {
          fail('SMTP_DEADLINE_BEFORE_ENVELOPE');
          return;
        }
        connection.send(envelope, body, (sendError, info) => {
          if (withheld) {
            fail('SMTP_DEADLINE_BEFORE_DATA_BODY');
          } else if (sendError) {
            fail(codeReason(sendError), sendError);
          } else {
            succeed(info.response);
          }
        });
      });
    });
  }
}
