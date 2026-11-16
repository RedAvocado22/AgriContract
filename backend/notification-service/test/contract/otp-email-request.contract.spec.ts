/**
 * Contract test: parseOtpEmailRequest against OtpEmailRequest and the
 * sendOtpEmail operation of contracts/openapi/golden-flow-api.yaml, read from
 * the repository (like the Java services read ../../contracts). If the
 * contract changes, this fails until the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { load } from 'js-yaml';
import {
  OTP_EMAIL_REQUEST_FIELDS,
  OTP_EMAIL_REQUIRED_FIELDS,
  OTP_PURPOSES,
  OtpEmailRequestInvalidError,
  parseOtpEmailRequest,
} from '../../src/application/otp/otp-email-request';
import { otpRequest, verifyEmailRequest } from '../support/otp';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/openapi/golden-flow-api.yaml',
);

interface OpenApi {
  paths: Record<string, Record<string, Record<string, unknown>>>;
  components: {
    schemas: Record<string, { required: string[]; properties: object }>;
    securitySchemes: Record<string, { in: string; name: string }>;
  };
}

const api = load(readFileSync(CONTRACT, 'utf8')) as OpenApi;
const schema = api.components.schemas.OtpEmailRequest;
const operation = api.paths['/internal/v1/notifications/otp-email'].post;

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
const contractAccepts = ajv.compile(schema);

const codeAccepts = (body: unknown): boolean => {
  try {
    parseOtpEmailRequest(body);
    return true;
  } catch (err) {
    if (err instanceof OtpEmailRequestInvalidError) return false;
    throw err;
  }
};

describe('OtpEmailRequest contract (golden-flow-api.yaml)', () => {
  it('has the operation, path and service auth the controller implements', () => {
    expect(operation.operationId).toBe('sendOtpEmail');
    expect(operation.security).toEqual([{ serviceAuth: [] }]);
    expect(api.components.securitySchemes.serviceAuth).toMatchObject({
      in: 'header',
      name: 'X-Internal-Secret',
    });
    expect(Object.keys(operation.responses as object).sort()).toEqual([
      '200',
      '5XX',
    ]);
  });

  /*
   * contractId and signedContentHash are not in `required`: the schema's
   * if/then/else requires them for the contract purposes and forbids them for
   * VERIFY_EMAIL, which the verdicts below check case by case.
   */
  it('declares exactly the fields the code reads, and requires those of every purpose', () => {
    expect(Object.keys(schema.properties).sort()).toEqual(
      [...OTP_EMAIL_REQUEST_FIELDS].sort(),
    );
    expect([...schema.required].sort()).toEqual(
      [...OTP_EMAIL_REQUIRED_FIELDS].sort(),
    );
  });

  it('declares the purposes the code has a template for, SIGN_CONTRACT by default', () => {
    const purpose = (
      schema.properties as Record<string, { enum: string[]; default: string }>
    ).purpose;
    expect([...purpose.enum].sort()).toEqual([...OTP_PURPOSES].sort());
    expect(purpose.default).toBe('SIGN_CONTRACT');
  });

  const base = otpRequest();
  const without = (field: string, from: object = base) =>
    Object.fromEntries(Object.entries(from).filter(([key]) => key !== field));
  const verify = verifyEmailRequest();
  const { contractId, signedContentHash } = base;
  const noContract = without('signedContentHash', without('contractId'));

  it.each<[string, unknown]>([
    ['the sample request', base],
    [
      'an expiry with an offset',
      { ...base, expiresAt: '2026-11-04T17:15:00.5+07:00' },
    ],
    [
      'a UUID requestId',
      { ...base, requestId: '00000000-0000-4000-8000-000000000000' },
    ],
    ['a missing requestId', without('requestId')],
    ['a missing otpCode', without('otpCode')],
    ['a missing expiresAt', without('expiresAt')],
    [
      'a missing purpose (a signing code, as contract-service sends it)',
      without('purpose'),
    ],
    ['purpose CANCEL_ACTIVATION', { ...base, purpose: 'CANCEL_ACTIVATION' }],
    ['an unknown purpose', { ...base, purpose: 'TERMINATE_CONTRACT' }],
    ['a lower-case purpose', { ...base, purpose: 'cancel_activation' }],
    ['a null purpose', { ...base, purpose: null }],
    ['an extra field', { ...base, extra: 1 }],
    ['a 5-digit otpCode', { ...base, otpCode: '12345' }],
    ['a numeric otpCode', { ...base, otpCode: 123456 }],
    ['an upper-case hash', { ...base, signedContentHash: 'A'.repeat(64) }],
    ['a short hash', { ...base, signedContentHash: 'a'.repeat(63) }],
    ['an invalid email', { ...base, recipientEmail: 'not-an-email' }],
    ['a date without time', { ...base, expiresAt: '2026-11-04' }],
    ['a date-time without zone', { ...base, expiresAt: '2026-11-04T10:15:00' }],
    ['an array body', [base]],
    // The contract fields, which depend on the purpose (#344).
    ['a missing contractId', without('contractId')],
    ['a missing signedContentHash', without('signedContentHash')],
    [
      'no contract fields and no purpose (a signing code)',
      without('purpose', noContract),
    ],
    [
      'no contract fields with SIGN_CONTRACT',
      { ...noContract, purpose: 'SIGN_CONTRACT' },
    ],
    [
      'no contract fields with CANCEL_ACTIVATION',
      { ...noContract, purpose: 'CANCEL_ACTIVATION' },
    ],
    [
      'no contract fields with an unknown purpose',
      { ...noContract, purpose: 'VERIFY' },
    ],
    ['the VERIFY_EMAIL request user-service sends', verify],
    ['VERIFY_EMAIL with a contractId', { ...verify, contractId }],
    ['VERIFY_EMAIL with a signedContentHash', { ...verify, signedContentHash }],
    [
      'VERIFY_EMAIL with both contract fields',
      { ...verify, contractId, signedContentHash },
    ],
    ['VERIFY_EMAIL with a null contractId', { ...verify, contractId: null }],
    ['VERIFY_EMAIL with a missing otpCode', without('otpCode', verify)],
    [
      'VERIFY_EMAIL with a missing recipientUserId',
      without('recipientUserId', verify),
    ],
    ['VERIFY_EMAIL with an invalid email', { ...verify, recipientEmail: 'x' }],
    ['a lower-case verify_email', { ...verify, purpose: 'verify_email' }],
  ])('agrees with the schema on %s', (_case, body) => {
    expect(codeAccepts(body)).toBe(contractAccepts(body));
  });

  /*
   * Agreement alone would pass if both refused everything: pin the verdicts
   * #422 is about.
   */
  it('accepts VERIFY_EMAIL only without a contract, and a contract code only with one', () => {
    expect(contractAccepts(verify)).toBe(true);
    expect(contractAccepts({ ...verify, contractId })).toBe(false);
    expect(contractAccepts({ ...verify, signedContentHash })).toBe(false);
    expect(contractAccepts(base)).toBe(true);
    expect(contractAccepts(without('purpose', noContract))).toBe(false);
    expect(codeAccepts(verify)).toBe(true);
    expect(codeAccepts(without('purpose', noContract))).toBe(false);
  });

  /*
   * Where the code is deliberately stricter than the contract (see
   * otp-email-request.ts): the schema accepts these, the endpoint answers 400.
   */
  it.each<[string, unknown]>([
    [
      'a requestId longer than notification_logs.event_id',
      { ...base, requestId: 'x'.repeat(37) },
    ],
    ['an empty contractId', { ...base, contractId: '' }],
    ['an empty recipientUserId', { ...base, recipientUserId: '' }],
  ])('is stricter than the schema on %s', (_case, body) => {
    expect(contractAccepts(body)).toBe(true);
    expect(codeAccepts(body)).toBe(false);
  });
});
