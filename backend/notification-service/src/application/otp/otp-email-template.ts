import type { NotificationKind } from '../../domain/notification-log';
import type { OtpEmailRequest, OtpPurpose } from './otp-email-request';
import { vietnamTime } from '../vietnam-time';

/** Template identity, stored in notification_logs.template_version (design §3.1). */
export const OTP_EMAIL_TEMPLATE_VERSION = 'otp-email/v1';

/*
 * The kind of the OTP email (#115). This endpoint is a synchronous command,
 * not a queue event, so eventType names the command the way the Phase 2
 * notification commands are named, and event_id holds the caller's requestId
 * (design §2.2). notificationType is stored in notification_logs and is part
 * of the dedup key: do not rename it.
 */
export const OTP_EMAIL_KIND = {
  eventType: 'notification.otp_email_requested',
  notificationType: 'CONTRACT_SIGNING_OTP',
  templateVersion: OTP_EMAIL_TEMPLATE_VERSION,
} as const satisfies NotificationKind;

export const OTP_EMAIL_SUBJECT = '[AgriContract] Your contract signing code';

/**
 * Stored in notification_logs.body instead of the rendered text, which holds
 * the OTP (design §2.2: the OTP is rendered in memory and never stored).
 */
export const OTP_EMAIL_LOG_BODY = logBody(OTP_EMAIL_TEMPLATE_VERSION);

/*
 * The code that confirms a party's request to cancel a SIGNED contract whose
 * deposit lock failed (purpose CANCEL_ACTIVATION, D42). Design §2.2: same
 * endpoint, dedup and log as the signing code, its own template and
 * notification_type. Same rule as above: do not rename notificationType.
 */
export const ACTIVATION_CANCEL_OTP_TEMPLATE_VERSION =
  'otp-activation-cancel/v1';

export const ACTIVATION_CANCEL_OTP_KIND = {
  eventType: 'notification.otp_email_requested',
  notificationType: 'ACTIVATION_CANCEL_OTP',
  templateVersion: ACTIVATION_CANCEL_OTP_TEMPLATE_VERSION,
} as const satisfies NotificationKind;

export const ACTIVATION_CANCEL_OTP_SUBJECT =
  '[AgriContract] Your code to cancel a contract';

export const ACTIVATION_CANCEL_OTP_LOG_BODY = logBody(
  ACTIVATION_CANCEL_OTP_TEMPLATE_VERSION,
);

function logBody(templateVersion: string): string {
  return `[${templateVersion}: body not stored, it contains the OTP]`;
}

/** What the use case needs to claim and log the email of one purpose. */
export interface OtpEmailTemplate {
  kind: NotificationKind;
  subject: string;
  /** Stored in notification_logs.body in place of the text, which holds the OTP. */
  logBody: string;
  /** The lines before the code. */
  intro: readonly string[];
  /** The lines after the fingerprint. */
  outro: readonly string[];
}

const DO_NOT_SHARE =
  'Do not share this code with anyone. AgriContract staff will never';

export const OTP_EMAIL_TEMPLATES: Record<OtpPurpose, OtpEmailTemplate> = {
  SIGN_CONTRACT: {
    kind: OTP_EMAIL_KIND,
    subject: OTP_EMAIL_SUBJECT,
    logBody: OTP_EMAIL_LOG_BODY,
    intro: ['Use this one-time code to sign your AgriContract contract:'],
    outro: [
      DO_NOT_SHARE,
      'ask for it. If you did not ask to sign this contract, ignore this',
      'email.',
    ],
  },
  CANCEL_ACTIVATION: {
    kind: ACTIVATION_CANCEL_OTP_KIND,
    subject: ACTIVATION_CANCEL_OTP_SUBJECT,
    logBody: ACTIVATION_CANCEL_OTP_LOG_BODY,
    intro: [
      'Use this one-time code to confirm your request to cancel your',
      'AgriContract contract. The contract was signed but never became',
      'active, because the deposits could not be locked.',
    ],
    outro: [
      'Your cancel request is recorded only once you enter this code.',
      '',
      DO_NOT_SHARE,
      'ask for it. If you did not ask to cancel this contract, ignore this',
      'email: nothing changes without the code.',
    ],
  },
};

export interface RenderedEmail {
  subject: string;
  body: string;
}

/**
 * Subject without the code (subjects show in notifications and mail lists);
 * body with it, on a line of its own. The template follows `purpose`.
 *
 * Lines stay within 76 characters (for contract ids up to 66 characters, a
 * UUID is 36): nodemailer then sends the text as 7bit. A longer line makes it
 * switch the whole body to quoted-printable, whose soft line breaks can split
 * the code, the contract id or the hash in the raw message.
 */
export function renderOtpEmail(request: OtpEmailRequest): RenderedEmail {
  const template = OTP_EMAIL_TEMPLATES[request.purpose];
  const expiresAt = new Date(request.expiresAt);
  return {
    subject: template.subject,
    body: [
      ...template.intro,
      '',
      request.otpCode,
      '',
      `Contract: ${request.contractId}`,
      `Expires: ${vietnamTime(expiresAt)} Vietnam time (${expiresAt.toISOString()} UTC)`,
      '',
      'Before you enter the code, check that the contract fingerprint',
      '(SHA-256) shown on screen is:',
      request.signedContentHash,
      '',
      ...template.outro,
    ].join('\n'),
  };
}
