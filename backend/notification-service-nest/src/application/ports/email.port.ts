export const EMAIL_PORT = Symbol('EMAIL_PORT');

export interface EmailMessage {
  to: string;
  subject: string;
  /** Plain text, as the Java service sends. */
  body: string;
}

export interface EmailPort {
  /** Resolves once the mail server accepted the message; rejects otherwise. */
  send(message: EmailMessage): Promise<void>;
}
