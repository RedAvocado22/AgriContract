export const EMAIL_PORT = Symbol('EMAIL_PORT');

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

export interface EmailPort {
  /** Resolves once the mail server accepted the message; rejects with EmailDeliveryError otherwise. */
  send(message: EmailMessage): Promise<void>;
}
