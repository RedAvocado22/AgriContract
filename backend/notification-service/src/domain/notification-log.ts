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

/**
 * What an email is: the event it answers and its template semantic (design
 * §3.1). One event may send several notification types, and two events may
 * send the same type.
 */
export interface NotificationKind {
  /** Routing key of the event or command, for routing and audit (`event_type`). */
  eventType: string;
  /** Template semantic, part of the dedup key (`notification_type`). */
  notificationType: string;
}

/**
 * Identifies one email: unique in `notification_logs` as
 * `uq_event_recipient_type (event_id, recipient_email, notification_type)`.
 */
export interface NotificationKey {
  eventId: string;
  recipientEmail: string;
  notificationType: string;
}

export interface NotificationLogProps {
  notificationId: string;
  eventId: string;
  eventType: string;
  notificationType: string;
  /**
   * `recipient_email`. Phase 1 code also writes it to `user_id`, which held
   * the recipient email before V2 (as in Java).
   */
  recipientEmail: string;
  channel: NotificationChannel;
  subject: string;
  body: string;
  status: NotificationStatus;
  retryCount: number;
}

/** One email of one type to one recipient for one event; its NotificationKey is unique. */
export class NotificationLog {
  private constructor(private props: NotificationLogProps) {}

  static create(
    eventId: string,
    kind: NotificationKind,
    recipientEmail: string,
    channel: NotificationChannel,
    subject: string,
    body: string,
  ): NotificationLog {
    return new NotificationLog({
      notificationId: randomUUID(),
      eventId,
      eventType: kind.eventType,
      notificationType: kind.notificationType,
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
  get eventType(): string {
    return this.props.eventType;
  }
  get notificationType(): string {
    return this.props.notificationType;
  }
  get recipientEmail(): string {
    return this.props.recipientEmail;
  }
  get key(): NotificationKey {
    return {
      eventId: this.props.eventId,
      recipientEmail: this.props.recipientEmail,
      notificationType: this.props.notificationType,
    };
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
