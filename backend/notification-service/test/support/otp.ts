import type { OtpEmailRequest } from '../../src/application/otp/otp-email-request';

/** A valid OtpEmailRequest; `otpCode` is distinctive so tests can search for leaks. */
export function otpRequest(
  overrides: Partial<OtpEmailRequest> = {},
): OtpEmailRequest {
  return {
    requestId: '8b6f3c1e-2a4d-4f7b-9c1e-0d2a3b4c5d6e',
    recipientUserId: '1de004cd-8493-463c-8f32-6c7b75bcbf16',
    recipientEmail: 'buyer1@test.com',
    contractId: 'ctr-otp-1',
    otpCode: '482913',
    signedContentHash: 'a'.repeat(64),
    expiresAt: '2026-11-04T10:15:00Z',
    ...overrides,
  };
}
