/*
 * OtpEmailRequest of contracts/openapi/golden-flow-api.yaml
 * (POST /internal/v1/notifications/otp-email). The contract test
 * (test/contract/otp-email-request.contract.spec.ts) checks these fields and
 * verdicts against the schema itself.
 */

/** The fields every purpose carries. */
interface OtpEmailRequestBase {
  /** The caller's otpId; deduplicates repeated calls (stored as notification_logs.event_id). */
  requestId: string;
  recipientUserId: string;
  recipientEmail: string;
  /** Rendered into the email only; never stored or logged. */
  otpCode: string;
  /** UTC instant, as sent by the caller. */
  expiresAt: string;
}

/**
 * A code that confirms an action on a contract (contract-service): signing,
 * a cancel request on a contract whose activation failed (D42), or ending a
 * milestone dispute by concession or agreed settlement (D57).
 */
export interface ContractOtpEmailRequest extends OtpEmailRequestBase {
  /**
   * Which action the code confirms; selects the template. Optional on the
   * wire, absent meaning SIGN_CONTRACT, so it is always set once parsed.
   */
  purpose: ContractOtpPurpose;
  contractId: string;
  signedContentHash: string;
}

/** The code that proves the email given at registration (user-service, #344, D45). */
export interface VerifyEmailOtpRequest extends OtpEmailRequestBase {
  purpose: 'VERIFY_EMAIL';
}

export type OtpEmailRequest = ContractOtpEmailRequest | VerifyEmailOtpRequest;

export const CONTRACT_OTP_PURPOSES = [
  'SIGN_CONTRACT',
  'CANCEL_ACTIVATION',
  'CONCEDE_DISPUTE',
  'SETTLE_DISPUTE',
] as const;
export type ContractOtpPurpose = (typeof CONTRACT_OTP_PURPOSES)[number];
export const OTP_PURPOSES = [...CONTRACT_OTP_PURPOSES, 'VERIFY_EMAIL'] as const;
export type OtpPurpose = (typeof OTP_PURPOSES)[number];
const DEFAULT_PURPOSE: ContractOtpPurpose = 'SIGN_CONTRACT';

/** The fields the contract requires whatever the purpose. */
export const OTP_EMAIL_REQUIRED_FIELDS = [
  'requestId',
  'recipientUserId',
  'recipientEmail',
  'otpCode',
  'expiresAt',
] as const satisfies readonly (keyof OtpEmailRequestBase)[];

/**
 * Required for the contract purposes (SIGN_CONTRACT, CANCEL_ACTIVATION,
 * CONCEDE_DISPUTE, SETTLE_DISPUTE or purpose absent), and must be absent for
 * VERIFY_EMAIL.
 */
export const OTP_EMAIL_CONTRACT_FIELDS = [
  'contractId',
  'signedContentHash',
] as const satisfies readonly (keyof ContractOtpEmailRequest)[];

/** Every field the contract declares. */
export const OTP_EMAIL_REQUEST_FIELDS = [
  ...OTP_EMAIL_REQUIRED_FIELDS,
  ...OTP_EMAIL_CONTRACT_FIELDS,
  'purpose',
] as const;

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

type CheckedField =
  | (typeof OTP_EMAIL_REQUIRED_FIELDS)[number]
  | (typeof OTP_EMAIL_CONTRACT_FIELDS)[number];

const VALID: Record<CheckedField, (text: string) => boolean> = {
  requestId: (s) => REQUEST_ID.test(s),
  recipientUserId: (s) => s.length > 0 && s.length <= MAX_ID_LENGTH,
  recipientEmail: (s) => s.length <= MAX_EMAIL_LENGTH && EMAIL.test(s),
  contractId: (s) => s.length > 0 && s.length <= MAX_ID_LENGTH,
  otpCode: (s) => OTP_CODE.test(s),
  signedContentHash: (s) => SHA256_HEX.test(s),
  expiresAt: (s) => DATE_TIME.test(s) && !Number.isNaN(Date.parse(s)),
};

/** Problems are reported in this order, the order the contract lists the fields in. */
const FIELD_ORDER: readonly CheckedField[] = [
  'requestId',
  'recipientUserId',
  'recipientEmail',
  'contractId',
  'otpCode',
  'signedContentHash',
  'expiresAt',
];

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

  // Optional: absent is SIGN_CONTRACT; null or any other value is invalid
  // (the contract's enum has no null).
  const purpose = value.purpose;
  const purposeValid =
    purpose === undefined ||
    (OTP_PURPOSES as readonly unknown[]).includes(purpose);
  // As the schema's if/then/else: only an explicit VERIFY_EMAIL drops the
  // contract fields; any other purpose, even an invalid one, requires them.
  const verifyEmail = purpose === 'VERIFY_EMAIL';

  for (const field of FIELD_ORDER) {
    const raw = value[field];
    const contractField = (
      OTP_EMAIL_CONTRACT_FIELDS as readonly string[]
    ).includes(field);
    if (verifyEmail && contractField) {
      if (raw !== undefined)
        problems.push(`${field} is not allowed for purpose VERIFY_EMAIL`);
    } else if (raw === undefined) problems.push(`${field} is required`);
    else if (typeof raw !== 'string' || !VALID[field](raw))
      problems.push(`${field} is invalid`);
  }
  if (!purposeValid) problems.push('purpose is invalid');

  if (problems.length > 0) throw new OtpEmailRequestInvalidError(problems);
  const base = Object.fromEntries(
    OTP_EMAIL_REQUIRED_FIELDS.map((field) => [field, value[field]]),
  ) as unknown as OtpEmailRequestBase;
  if (verifyEmail) return { ...base, purpose: 'VERIFY_EMAIL' };
  return {
    ...base,
    contractId: value.contractId as string,
    signedContentHash: value.signedContentHash as string,
    purpose: (purpose as ContractOtpPurpose | undefined) ?? DEFAULT_PURPOSE,
  };
}
