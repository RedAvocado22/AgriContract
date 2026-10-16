import { randomInt, randomUUID } from 'node:crypto';
import {
  EmailDeliveryError,
  type DbCallOptions,
  type DeliveryCertainty,
  type EmailMessage,
  type EmailSendOptions,
  type EmailSendResult,
  type EmailSender,
  type NotificationLogStore,
} from '../../src/application/ports.js';
import type { OtpEmailRequest } from '../../src/application/sendOtpEmail.js';
import {
  persistableBody,
  type NewNotificationLog,
  type NotificationKey,
  type NotificationLog,
  type NotificationStatus,
} from '../../src/domain/notificationLog.js';

export const SECRET = 'test-internal-secret-value';

/** A fresh 6-digit code, so each test checks for the exact value it submitted. */
export function randomOtp(): string {
  return String(randomInt(100_000, 1_000_000));
}

export function otpRequest(overrides: Partial<OtpEmailRequest> = {}): OtpEmailRequest {
  return {
    requestId: randomUUID(),
    recipientUserId: 'user-42',
    recipientEmail: 'signer@test.local',
    contractId: 'ctr-2026-0001',
    otpCode: randomOtp(),
    signedContentHash: 'ab'.repeat(32),
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    ...overrides,
  };
}

function keyOf(key: NotificationKey): string {
  return JSON.stringify([key.eventId, key.recipientEmail, key.notificationType]);
}

type StoreMethod = 'reserve' | 'markSent' | 'markFailed' | 'findByKey';

/** Same contract as NotificationLogRepository; reserve is atomic like the unique index. */
export class InMemoryNotificationLogStore implements NotificationLogStore {
  readonly rows = new Map<string, NotificationLog>();
  failOn: Partial<Record<StoreMethod, Error>> = {};
  /** timeoutMs each call received, in call order. */
  readonly calls: { method: StoreMethod; timeoutMs: number | undefined }[] = [];
  /** Runs at the start of every call; tests use it to advance a fake clock. */
  beforeCall: ((method: StoreMethod) => void) | undefined;

  async reserve(log: NewNotificationLog, options?: DbCallOptions): Promise<boolean> {
    await this.enter('reserve', options);
    const key = keyOf(log);
    if (this.rows.has(key)) {
      return false;
    }
    const now = new Date();
    this.rows.set(key, {
      ...log,
      body: persistableBody(log),
      channel: 'EMAIL',
      status: 'PENDING',
      retryCount: 0,
      providerMessageId: null,
      failureReason: null,
      sentAt: null,
      createdAt: now,
      updatedAt: now,
    });
    return true;
  }

  async markSent(
    notificationId: string,
    providerMessageId: string,
    sentAt: Date,
    options?: DbCallOptions,
  ): Promise<boolean> {
    await this.enter('markSent', options);
    return this.update(notificationId, { status: 'SENT', providerMessageId, sentAt });
  }

  async markFailed(
    notificationId: string,
    failureReason: string,
    options?: DbCallOptions,
  ): Promise<boolean> {
    await this.enter('markFailed', options);
    const row = this.byId(notificationId);
    return this.update(notificationId, {
      status: 'FAILED',
      failureReason,
      retryCount: (row?.retryCount ?? 0) + 1,
    });
  }

  async findByKey(key: NotificationKey, options?: DbCallOptions): Promise<NotificationLog | null> {
    await this.enter('findByKey', options);
    return this.rows.get(keyOf(key)) ?? null;
  }

  /** Puts a row in a given state, as an earlier request would have left it. */
  seed(request: OtpEmailRequest, status: NotificationStatus): void {
    const now = new Date();
    this.rows.set(
      keyOf({
        eventId: request.requestId,
        recipientEmail: request.recipientEmail,
        notificationType: 'OTP_SIGN',
      }),
      {
        notificationId: randomUUID(),
        eventId: request.requestId,
        eventType: 'otp.sign',
        notificationType: 'OTP_SIGN',
        recipientUserId: request.recipientUserId,
        recipientEmail: request.recipientEmail,
        templateVersion: 'OTP_SIGN_V1',
        subject: '[AgriContract] Your contract signing code',
        body: '[redacted:OTP_SIGN_V1]',
        channel: 'EMAIL',
        status,
        retryCount: status === 'FAILED' ? 1 : 0,
        providerMessageId: status === 'SENT' ? '<earlier@test>' : null,
        failureReason: status === 'FAILED' ? 'SMTP_ETIMEDOUT' : null,
        sentAt: status === 'SENT' ? now : null,
        createdAt: now,
        updatedAt: now,
      },
    );
  }

  /** True when any stored column contains value. */
  contains(value: string): boolean {
    return [...this.rows.values()].some((row) => JSON.stringify(row).includes(value));
  }

  private async enter(method: StoreMethod, options: DbCallOptions | undefined): Promise<void> {
    this.calls.push({ method, timeoutMs: options?.timeoutMs });
    this.beforeCall?.(method);
    await Promise.resolve();
    const failure = this.failOn[method];
    if (failure) {
      throw failure;
    }
  }

  private byId(notificationId: string): NotificationLog | undefined {
    return [...this.rows.values()].find((row) => row.notificationId === notificationId);
  }

  private update(notificationId: string, changes: Partial<NotificationLog>): boolean {
    const row = this.byId(notificationId);
    if (row === undefined) {
      return false;
    }
    Object.assign(row, changes, { updatedAt: new Date() });
    return true;
  }
}

/** Records every message; fails or holds sends on demand. */
export class FakeEmailSender implements EmailSender {
  readonly sent: EmailMessage[] = [];
  readonly options: EmailSendOptions[] = [];
  failWith: EmailDeliveryError | Error | undefined;
  /** When set, send waits for this promise before answering. */
  gate: Promise<void> | undefined;
  /** Runs inside send; tests use it to advance a fake clock. */
  onSend: (() => void) | undefined;

  async send(message: EmailMessage, options: EmailSendOptions): Promise<EmailSendResult> {
    this.options.push(options);
    this.onSend?.();
    await this.gate;
    if (this.failWith !== undefined) {
      throw this.failWith;
    }
    this.sent.push(message);
    return { providerMessageId: `<${String(this.sent.length)}@fake>` };
  }
}

export function deliveryError(
  reason: string,
  certainty: DeliveryCertainty = 'NOT_SENT',
): EmailDeliveryError {
  return new EmailDeliveryError(reason, certainty);
}

function collectStrings(value: unknown, into: string[]): void {
  if (typeof value === 'string') {
    into.push(value);
  } else if (Array.isArray(value)) {
    for (const item of value) collectStrings(item, into);
  } else if (typeof value === 'object' && value !== null) {
    for (const [key, item] of Object.entries(value)) {
      into.push(key);
      collectStrings(item, into);
    }
  }
}

/**
 * Collects pino output lines. Leak checks look at string values and keys only: numeric fields
 * such as `time` (epoch ms) can contain any 6-digit sequence by chance.
 */
export class LogCapture {
  readonly lines: string[] = [];

  write(line: string): void {
    this.lines.push(line);
  }

  /** True when value appears in a logged string, or anywhere in a line that is not JSON. */
  contains(value: string): boolean {
    return this.lines.some((line) => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(line);
      } catch {
        return line.includes(value);
      }
      const strings: string[] = [];
      collectStrings(parsed, strings);
      return strings.some((text) => text.includes(value));
    });
  }
}

export function deferred(): { promise: Promise<void>; resolve: () => void } {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

/** A clock tests move by hand. */
export class ManualClock {
  constructor(public ms = Date.parse('2026-10-16T08:00:00.000Z')) {}

  now = (): Date => new Date(this.ms);

  advance(ms: number): void {
    this.ms += ms;
  }
}
