import { randomUUID } from 'node:crypto';

/** Same values as the Java enum; stored as VARCHAR(20) in `notification_logs.status`. */
export enum NotificationStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
}

/** Same values as the Java enum; stored as VARCHAR(20) in `notification_logs.channel`. */
export enum NotificationChannel {
  EMAIL = 'EMAIL',
}

export interface NotificationLogProps {
  notificationId: string;
  eventId: string;
  /** Persisted in the `user_id` column, which holds the recipient email (as in Java). */
  recipientEmail: string;
  channel: NotificationChannel;
  subject: string;
  body: string;
  status: NotificationStatus;
  retryCount: number;
}

/** One email to one recipient for one event; `(eventId, recipientEmail)` is unique. */
export class NotificationLog {
  private constructor(private props: NotificationLogProps) {}

  static create(
    eventId: string,
    recipientEmail: string,
    channel: NotificationChannel,
    subject: string,
    body: string,
  ): NotificationLog {
    return new NotificationLog({
      notificationId: randomUUID(),
      eventId,
      recipientEmail,
      channel,
      subject,
      body,
      status: NotificationStatus.PENDING,
      retryCount: 0,
    });
  }

  static reconstitute(props: NotificationLogProps): NotificationLog {
    return new NotificationLog({ ...props });
  }

  get notificationId(): string {
    return this.props.notificationId;
  }
  get eventId(): string {
    return this.props.eventId;
  }
  get recipientEmail(): string {
    return this.props.recipientEmail;
  }
  get channel(): NotificationChannel {
    return this.props.channel;
  }
  get subject(): string {
    return this.props.subject;
  }
  get body(): string {
    return this.props.body;
  }
  get status(): NotificationStatus {
    return this.props.status;
  }
  get retryCount(): number {
    return this.props.retryCount;
  }

  markSent(): void {
    this.props.status = NotificationStatus.SENT;
  }

  markFailed(): void {
    this.props.retryCount += 1;
    this.props.status = NotificationStatus.FAILED;
  }
}
