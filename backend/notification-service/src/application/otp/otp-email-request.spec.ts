import { otpRequest, verifyEmailRequest } from '../../../test/support/otp';
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

  describe('purpose (D42)', () => {
    const withoutPurpose = (): Record<string, unknown> => {
      const rest: Record<string, unknown> = { ...otpRequest() };
      delete rest.purpose;
      return rest;
    };

    it('is SIGN_CONTRACT when absent, as contract-service sends a signing code', () => {
      expect(parseOtpEmailRequest(withoutPurpose()).purpose).toBe(
        'SIGN_CONTRACT',
      );
    });

    it.each([
      'SIGN_CONTRACT',
      'CANCEL_ACTIVATION',
      'CONCEDE_DISPUTE',
      'SETTLE_DISPUTE',
    ])('accepts %s', (purpose) => {
      expect(
        parseOtpEmailRequest({ ...withoutPurpose(), purpose }).purpose,
      ).toBe(purpose);
    });

    it.each([
      null,
      'cancel_activation',
      'concede_dispute',
      'TERMINATE_CONTRACT',
      1,
      '',
    ])('rejects %p', (purpose) => {
      expect(problemsOf({ ...withoutPurpose(), purpose })).toEqual([
        'purpose is invalid',
      ]);
    });

    it('is not reported missing', () => {
      expect(problemsOf(withoutPurpose())).toEqual([]);
    });
  });

  describe('purpose VERIFY_EMAIL (#344)', () => {
    it('accepts the request user-service sends, without a contract, and returns exactly its fields', () => {
      const parsed = parseOtpEmailRequest(verifyEmailRequest());

      expect(parsed).toEqual(verifyEmailRequest());
      expect(parsed).not.toHaveProperty('contractId');
      expect(parsed).not.toHaveProperty('signedContentHash');
    });

    it.each(['contractId', 'signedContentHash'])(
      'rejects a %s, whatever its value',
      (field) => {
        for (const value of ['ctr-otp-1', 'a'.repeat(64), '', null]) {
          expect(
            problemsOf({ ...verifyEmailRequest(), [field]: value }),
          ).toEqual([`${field} is not allowed for purpose VERIFY_EMAIL`]);
        }
      },
    );

    it('rejects both contract fields, one problem each', () => {
      const { contractId, signedContentHash } = otpRequest();
      expect(
        problemsOf({ ...verifyEmailRequest(), contractId, signedContentHash }),
      ).toEqual([
        'contractId is not allowed for purpose VERIFY_EMAIL',
        'signedContentHash is not allowed for purpose VERIFY_EMAIL',
      ]);
    });

    it('still requires every other field', () => {
      expect(problemsOf({ purpose: 'VERIFY_EMAIL' })).toEqual([
        'requestId is required',
        'recipientUserId is required',
        'recipientEmail is required',
        'otpCode is required',
        'expiresAt is required',
      ]);
    });

    it.each([
      ['otpCode', '12345'],
      ['recipientEmail', 'not-an-email'],
      ['expiresAt', '2026-11-04T10:15:00'],
    ])('validates %s = %p as for a contract code', (field, value) => {
      expect(problemsOf({ ...verifyEmailRequest(), [field]: value })).toEqual([
        `${field} is invalid`,
      ]);
    });

    /*
     * Only VERIFY_EMAIL drops the contract fields: a contract purpose, an
     * absent one or an invalid one still requires both (no regression for the
     * signing and cancel codes).
     */
    it.each([
      undefined,
      'SIGN_CONTRACT',
      'CANCEL_ACTIVATION',
      'CONCEDE_DISPUTE',
      'SETTLE_DISPUTE',
    ])('still requires both contract fields for purpose %p', (purpose) => {
      expect(problemsOf({ ...verifyEmailRequest(), purpose })).toEqual([
        'contractId is required',
        'signedContentHash is required',
      ]);
    });

    it.each(['verify_email', 'VERIFY'])(
      'treats %p as an invalid purpose that needs a contract',
      (purpose) => {
        expect(problemsOf({ ...verifyEmailRequest(), purpose })).toEqual([
          'contractId is required',
          'signedContentHash is required',
          'purpose is invalid',
        ]);
      },
    );
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
