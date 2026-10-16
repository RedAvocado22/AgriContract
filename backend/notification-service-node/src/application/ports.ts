import type {
  NewNotificationLog,
  NotificationKey,
  NotificationLog,
} from '../domain/notificationLog.js';

export interface DbCallOptions {
  /** Cap on the call, including waiting for a pool connection. Unbounded when omitted. */
  timeoutMs?: number;
}

/**
 * A database call hit its time limit. The statement is not cancelled on the server: it may
 * still complete (and an INSERT or UPDATE commit) after this error was raised.
 */
export class DatabaseDeadlineError extends Error {
  readonly code = 'DB_DEADLINE_EXCEEDED';

  constructor(readonly phase: 'ACQUIRE' | 'QUERY') {
    super(
      `database call exceeded its time limit while ${phase === 'ACQUIRE' ? 'acquiring a connection' : 'running'}`,
    );
    this.name = 'DatabaseDeadlineError';
  }
}

/** Implemented by NotificationLogRepository. */
export interface NotificationLogStore {
  reserve(log: NewNotificationLog, options?: DbCallOptions): Promise<boolean>;
  markSent(
    notificationId: string,
    providerMessageId: string,
    sentAt: Date,
    options?: DbCallOptions,
  ): Promise<boolean>;
  markFailed(
    notificationId: string,
    failureReason: string,
    options?: DbCallOptions,
  ): Promise<boolean>;
  findByKey(key: NotificationKey, options?: DbCallOptions): Promise<NotificationLog | null>;
}

export interface EmailMessage {
  to: string;
  subject: string;
  text: string;
  html: string;
}

export interface EmailSendOptions {
  /** Epoch ms by which the provider must have answered. */
  deadlineAt: number;
}

export interface EmailSendResult {
  providerMessageId: string;
}

export interface EmailSender {
  /** Resolves once the provider accepted the message, rejects with EmailDeliveryError. */
  send(message: EmailMessage, options: EmailSendOptions): Promise<EmailSendResult>;
}

/**
 * - NOT_SENT: the provider cannot have accepted the message (it never received the complete
 *   message, or it answered with a rejection).
 * - UNKNOWN: the complete message was handed to the provider but no answer arrived in time.
 *   It may have been accepted and may still be delivered.
 */
export type DeliveryCertainty = 'NOT_SENT' | 'UNKNOWN';

/**
 * Provider failure. reason is built from error codes only, never from provider messages,
 * so it is safe to persist and log.
 */
export class EmailDeliveryError extends Error {
  constructor(
    readonly reason: string,
    readonly certainty: DeliveryCertainty = 'NOT_SENT',
  ) {
    super(reason);
    this.name = 'EmailDeliveryError';
  }
}

/** Subset of the pino logger the use cases write to. */
export interface OperationLogger {
  info(fields: object, message: string): void;
  warn(fields: object, message: string): void;
  error(fields: object, message: string): void;
}
