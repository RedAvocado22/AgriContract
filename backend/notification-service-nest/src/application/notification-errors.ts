/*
 * Errors whose messages are safe to log: they carry an event id and a failure
 * code, never a recipient address or payload text (review #172). Shared
 * eventing's handleDelivery logs err.message on every retry and dead-letter.
 */

/** Base class of the errors this service lets reach the delivery logs as they are. */
export class NotificationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

/** Retryable: another delivery still held a live claim on the same email after the claim wait. */
export class SendInProgressError extends NotificationError {
  constructor(readonly eventId: string) {
    super(
      `Event ${eventId}: another delivery is sending this email; retrying later`,
    );
  }
}

/**
 * Shutdown began while waiting for another delivery's live claim. The
 * consumer parks the message (no ack, no nack) so the broker redelivers it
 * after the restart; outside shutdown it is retryable like SendInProgressError.
 */
export class ClaimWaitAbortedError extends NotificationError {
  constructor(readonly eventId: string) {
    super(
      `Event ${eventId}: shutdown began while another delivery was sending this email`,
    );
  }
}

/** Retryable: the email or its log write failed; `code` names the cause. */
export class NotificationDeliveryError extends NotificationError {
  constructor(
    readonly eventId: string,
    readonly step: 'email' | 'log',
    readonly code: string,
  ) {
    super(
      `Event ${eventId}: ${step === 'email' ? 'email delivery' : 'notification log write'} failed (${code})`,
    );
  }
}

const SAFE_CODE = /^[A-Za-z][A-Za-z0-9_/.-]{0,63}$/;

/**
 * A failure code that is safe to log: the error's `code` (e.g. ER_LOCK_DEADLOCK,
 * ECONNREFUSED) when it looks like one, otherwise its class name. Never the
 * message, which may quote addresses or payload text.
 */
export function failureCode(err: unknown): string {
  const code = (err as { code?: unknown } | null)?.code;
  if (typeof code === 'string' && SAFE_CODE.test(code)) return code;
  if (err instanceof Error && SAFE_CODE.test(err.name)) return err.name;
  return 'UNKNOWN';
}
