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
  OtpEmailRequestInvalidError,
  parseOtpEmailRequest,
} from '../../src/application/otp/otp-email-request';
import { otpRequest } from '../support/otp';

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

  it('declares exactly the fields the code reads, all required', () => {
    expect(Object.keys(schema.properties).sort()).toEqual(
      [...OTP_EMAIL_REQUEST_FIELDS].sort(),
    );
    expect([...schema.required].sort()).toEqual(
      [...OTP_EMAIL_REQUEST_FIELDS].sort(),
    );
  });

  const base = otpRequest();
  const without = (field: string) =>
    Object.fromEntries(Object.entries(base).filter(([key]) => key !== field));

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
    ['an extra field', { ...base, extra: 1 }],
    ['a 5-digit otpCode', { ...base, otpCode: '12345' }],
    ['a numeric otpCode', { ...base, otpCode: 123456 }],
    ['an upper-case hash', { ...base, signedContentHash: 'A'.repeat(64) }],
    ['a short hash', { ...base, signedContentHash: 'a'.repeat(63) }],
    ['an invalid email', { ...base, recipientEmail: 'not-an-email' }],
    ['a date without time', { ...base, expiresAt: '2026-11-04' }],
    ['a date-time without zone', { ...base, expiresAt: '2026-11-04T10:15:00' }],
    ['an array body', [base]],
  ])('agrees with the schema on %s', (_case, body) => {
    expect(codeAccepts(body)).toBe(contractAccepts(body));
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
