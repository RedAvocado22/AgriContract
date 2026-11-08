export const EMAIL_PORT = Symbol('EMAIL_PORT');

/** The EmailPort of the synchronous OTP endpoint: same server, short timeouts. */
export const OTP_EMAIL_PORT = Symbol('OTP_EMAIL_PORT');

export interface EmailMessage {
  to: string;
  subject: string;
  /** Plain text, as the Java service sends. */
  body: string;
}

/**
 * The only error an EmailPort rejects with. It carries a failure code such as
 * `EENVELOPE/550` or `ECONNREFUSED` and nothing else: SMTP error texts quote
 * recipient addresses, which must not reach the logs.
 */
export class EmailDeliveryError extends Error {
  constructor(readonly code: string) {
    super(`email delivery failed (${code})`);
    this.name = 'EmailDeliveryError';
  }
}

/**
 * What the mail server answered when it accepted a message. The log keeps the
 * provider's id with the sent time (design §8), so a delivery can later be
 * traced at the provider — and a bounce, which quotes the Message-ID of the
 * mail it answers, matched back to the row that sent it (design §9).
 *
 * `providerMessageId` is null when the provider named no id. Nothing depends
 * on it: it is evidence, never a key, and an email the provider accepted
 * counts as SENT with or without one.
 */
export interface EmailReceipt {
  providerMessageId: string | null;
}

export interface EmailPort {
  /**
   * Resolves with the provider's receipt once the mail server accepted the
   * message; rejects with EmailDeliveryError otherwise.
   */
  send(message: EmailMessage): Promise<EmailReceipt>;
}
