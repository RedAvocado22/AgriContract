import { otpRequest } from '../../../test/support/otp';
import {
  OtpEmailRequestInvalidError,
  parseOtpEmailRequest,
} from './otp-email-request';

const problemsOf = (body: unknown): readonly string[] => {
  try {
    parseOtpEmailRequest(body);
  } catch (err) {
    if (err instanceof OtpEmailRequestInvalidError) return err.problems;
    throw err;
  }
  return [];
};

describe('parseOtpEmailRequest', () => {
  it('accepts a valid request and returns exactly the contract fields', () => {
    expect(parseOtpEmailRequest(otpRequest())).toEqual(otpRequest());
  });

  it('accepts an expiry with an offset and fractional seconds', () => {
    expect(
      problemsOf(otpRequest({ expiresAt: '2026-11-04T17:15:00.123+07:00' })),
    ).toEqual([]);
  });

  it.each([null, [], 'text', 42])('rejects a body that is %p', (body) => {
    expect(problemsOf(body)).toEqual(['body must be a JSON object']);
  });

  it('reports each missing field', () => {
    expect(problemsOf({})).toEqual([
      'requestId is required',
      'recipientUserId is required',
      'recipientEmail is required',
      'contractId is required',
      'otpCode is required',
      'signedContentHash is required',
      'expiresAt is required',
    ]);
  });

  it.each([
    ['requestId', ''],
    ['requestId', 'x'.repeat(37)],
    ['requestId', 'has space'],
    ['recipientUserId', ''],
    ['recipientEmail', 'not-an-email'],
    ['recipientEmail', `${'a'.repeat(250)}@x.io`],
    ['contractId', ''],
    ['otpCode', '12345'],
    ['otpCode', '1234567'],
    ['otpCode', '12a456'],
    ['signedContentHash', 'A'.repeat(64)],
    ['signedContentHash', 'a'.repeat(63)],
    ['expiresAt', '2026-11-04'],
    ['expiresAt', '2026-11-04T10:15:00'],
    ['expiresAt', '2026-13-45T10:15:00Z'],
    ['otpCode', 482913],
  ])('rejects %s = %p', (field, value) => {
    expect(problemsOf({ ...otpRequest(), [field]: value })).toEqual([
      `${field} is invalid`,
    ]);
  });

  it('rejects unknown fields without naming them', () => {
    expect(
      problemsOf({ ...otpRequest(), extra: 1, 'buyer2@test.com': 'x' }),
    ).toEqual(['2 unexpected field(s)']);
  });

  it('never quotes a value in its message', () => {
    const body = {
      ...otpRequest({ otpCode: '9x9x9x', recipientEmail: 'leak@@test.com' }),
    };
    let message = '';
    try {
      parseOtpEmailRequest(body);
    } catch (err) {
      message = (err as Error).message;
    }
    expect(message).toContain('otpCode is invalid');
    expect(message).not.toContain('9x9x9x');
    expect(message).not.toContain('leak');
  });
});
