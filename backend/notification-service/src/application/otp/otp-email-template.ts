import type { OtpEmailRequest } from './otp-email-request';

/** Template identity, for notification_logs.template_version once the V2 migration (#115) adds it. */
export const OTP_EMAIL_TEMPLATE_VERSION = 'otp-email/v1';

export const OTP_EMAIL_SUBJECT = '[AgriContract] Your contract signing code';

/**
 * Stored in notification_logs.body instead of the rendered text, which holds
 * the OTP (design §2.2: the OTP is rendered in memory and never stored).
 */
export const OTP_EMAIL_LOG_BODY = `[${OTP_EMAIL_TEMPLATE_VERSION}: body not stored, it contains the OTP]`;

export interface RenderedEmail {
  subject: string;
  body: string;
}

/**
 * Subject without the code (subjects show in notifications and mail lists);
 * body with it, on a line of its own.
 *
 * Lines stay within 76 characters (for contract ids up to 66 characters, a
 * UUID is 36): nodemailer then sends the text as 7bit. A longer line makes it
 * switch the whole body to quoted-printable, whose soft line breaks can split
 * the code, the contract id or the hash in the raw message.
 */
export function renderOtpEmail(request: OtpEmailRequest): RenderedEmail {
  const expiresAt = new Date(request.expiresAt);
  return {
    subject: OTP_EMAIL_SUBJECT,
    body: [
      'Use this one-time code to sign your AgriContract contract:',
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
      'Do not share this code with anyone. AgriContract staff will never',
      'ask for it. If you did not ask to sign this contract, ignore this',
      'email.',
    ].join('\n'),
  };
}

const VIETNAM_TIME = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** "2026-11-04 17:15:00" in Asia/Ho_Chi_Minh. */
function vietnamTime(instant: Date): string {
  const part = Object.fromEntries(
    VIETNAM_TIME.formatToParts(instant).map((p) => [p.type, p.value]),
  );
  return `${part.year}-${part.month}-${part.day} ${part.hour}:${part.minute}:${part.second}`;
}
