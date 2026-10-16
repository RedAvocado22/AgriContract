export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export type NotificationChannel = 'EMAIL';

/** Dedup key, matches unique index uq_event_recipient_type. */
export interface NotificationKey {
  eventId: string;
  recipientEmail: string;
  notificationType: string;
}

export interface NewNotificationLog extends NotificationKey {
  notificationId: string;
  eventType: string;
  /** Stored in column user_id, which stays NOT NULL. */
  recipientUserId: string;
  templateVersion: string;
  subject: string;
  body: string;
}

export interface NotificationLog extends NewNotificationLog {
  channel: NotificationChannel;
  status: NotificationStatus;
  retryCount: number;
  providerMessageId: string | null;
  failureReason: string | null;
  sentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export const OTP_NOTIFICATION_TYPE = 'OTP_SIGN';

/** Placeholder stored in place of a body that must not be persisted. */
export function redactedBody(templateVersion: string): string {
  return `[redacted:${templateVersion}]`;
}

/**
 * Body that may be persisted. OTP mail bodies contain the code, so only a placeholder
 * naming the template is stored (design §8).
 */
export function persistableBody(
  log: Pick<NewNotificationLog, 'notificationType' | 'templateVersion' | 'body'>,
): string {
  return log.notificationType === OTP_NOTIFICATION_TYPE
    ? redactedBody(log.templateVersion)
    : log.body;
}
