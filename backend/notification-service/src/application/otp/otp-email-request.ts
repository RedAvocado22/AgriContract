/*
 * OtpEmailRequest of contracts/openapi/golden-flow-api.yaml
 * (POST /internal/v1/notifications/otp-email). The contract test
 * (test/contract/otp-email-request.contract.spec.ts) checks these fields and
 * verdicts against the schema itself.
 */

export interface OtpEmailRequest {
  /** The caller's otpId; deduplicates repeated calls (stored as notification_logs.event_id). */
  requestId: string;
  recipientUserId: string;
  recipientEmail: string;
  contractId: string;
  /** Rendered into the email only; never stored or logged. */
  otpCode: string;
  signedContentHash: string;
  /** UTC instant, as sent by the caller. */
  expiresAt: string;
  /**
   * Which action the code confirms; selects the template (D42). Optional on
   * the wire, absent meaning SIGN_CONTRACT, so it is always set once parsed.
   */
  purpose: OtpPurpose;
}

export const OTP_PURPOSES = ['SIGN_CONTRACT', 'CANCEL_ACTIVATION'] as const;
export type OtpPurpose = (typeof OTP_PURPOSES)[number];
const DEFAULT_PURPOSE: OtpPurpose = 'SIGN_CONTRACT';

/** The fields the contract requires. */
export const OTP_EMAIL_REQUIRED_FIELDS = [
  'requestId',
  'recipientUserId',
  'recipientEmail',
  'contractId',
  'otpCode',
  'signedContentHash',
  'expiresAt',
] as const satisfies readonly (keyof OtpEmailRequest)[];

/** Every field the contract declares: the required ones, then `purpose`. */
export const OTP_EMAIL_REQUEST_FIELDS = [
  ...OTP_EMAIL_REQUIRED_FIELDS,
  'purpose',
] as const satisfies readonly (keyof OtpEmailRequest)[];

/**
 * The request does not match the contract. The message names fields only,
 * never their values: the body carries an OTP and an email address.
 */
export class OtpEmailRequestInvalidError extends Error {
  constructor(readonly problems: readonly string[]) {
    super(`Invalid OTP email request: ${problems.join('; ')}`);
    this.name = 'OtpEmailRequestInvalidError';
  }
}

/** ajv-formats "email" (full mode), which the contract test validates with. */
const EMAIL =
  /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i;
const OTP_CODE = /^[0-9]{6}$/;
const SHA256_HEX = /^[a-f0-9]{64}$/;
const DATE_TIME =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/i;

/*
 * Stricter than the contract, which only says "string":
 * - requestId goes into notification_logs.event_id, VARCHAR(36), and into log
 *   lines, so it is limited to 36 characters without spaces or control
 *   characters (an otpId is a UUID);
 * - recipientEmail goes into user_id, VARCHAR(255) (an address is at most 254);
 * - recipientUserId goes into recipient_user_id, VARCHAR(255) since V2 (#115),
 *   and contractId is rendered into the email: both must be non-empty and at
 *   most 255 characters, so the id is stored whole and the email stays small.
 */
const REQUEST_ID = /^[A-Za-z0-9._:-]{1,36}$/;
const MAX_EMAIL_LENGTH = 254;
const MAX_ID_LENGTH = 255;

/** Validates an already JSON-parsed body against the contract; throws OtpEmailRequestInvalidError. */
export function parseOtpEmailRequest(body: unknown): OtpEmailRequest {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new OtpEmailRequestInvalidError(['body must be a JSON object']);
  }
  const value = body as Record<string, unknown>;
  const problems: string[] = [];

  const unknownCount = Object.keys(value).filter(
    (key) => !(OTP_EMAIL_REQUEST_FIELDS as readonly string[]).includes(key),
  ).length;
  if (unknownCount > 0) {
    problems.push(`${unknownCount} unexpected field(s)`);
  }

  const check = (
    field: (typeof OTP_EMAIL_REQUIRED_FIELDS)[number],
    valid: (text: string) => boolean,
  ) => {
    const raw = value[field];
    if (raw === undefined) problems.push(`${field} is required`);
    else if (typeof raw !== 'string' || !valid(raw))
      problems.push(`${field} is invalid`);
  };

  check('requestId', (s) => REQUEST_ID.test(s));
  check('recipientUserId', (s) => s.length > 0 && s.length <= MAX_ID_LENGTH);
  check('recipientEmail', (s) => s.length <= MAX_EMAIL_LENGTH && EMAIL.test(s));
  check('contractId', (s) => s.length > 0 && s.length <= MAX_ID_LENGTH);
  check('otpCode', (s) => OTP_CODE.test(s));
  check('signedContentHash', (s) => SHA256_HEX.test(s));
  check('expiresAt', (s) => DATE_TIME.test(s) && !Number.isNaN(Date.parse(s)));
  // Optional: absent is SIGN_CONTRACT; null or any other value is invalid
  // (the contract's enum has no null).
  const purpose = value.purpose;
  if (
    purpose !== undefined &&
    !(OTP_PURPOSES as readonly unknown[]).includes(purpose)
  ) {
    problems.push('purpose is invalid');
  }

  if (problems.length > 0) throw new OtpEmailRequestInvalidError(problems);
  return {
    ...(Object.fromEntries(
      OTP_EMAIL_REQUIRED_FIELDS.map((field) => [field, value[field]]),
    ) as Omit<OtpEmailRequest, 'purpose'>),
    purpose: (purpose as OtpPurpose | undefined) ?? DEFAULT_PURPOSE,
  };
}
