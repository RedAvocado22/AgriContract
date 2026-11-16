import type {
  ContractOtpEmailRequest,
  OtpEmailRequest,
  VerifyEmailOtpRequest,
} from '../../src/application/otp/otp-email-request';
import { OTP_EMAIL_TEMPLATES } from '../../src/application/otp/otp-email-template';
import type { NotificationKey } from '../../src/domain/notification-log';

/** A valid contract OtpEmailRequest; `otpCode` is distinctive so tests can search for leaks. */
export function otpRequest(
  overrides: Partial<ContractOtpEmailRequest> = {},
): ContractOtpEmailRequest {
  return {
    requestId: '8b6f3c1e-2a4d-4f7b-9c1e-0d2a3b4c5d6e',
    recipientUserId: '1de004cd-8493-463c-8f32-6c7b75bcbf16',
    recipientEmail: 'buyer1@test.com',
    contractId: 'ctr-otp-1',
    otpCode: '482913',
    signedContentHash: 'a'.repeat(64),
    expiresAt: '2026-11-04T10:15:00Z',
    purpose: 'SIGN_CONTRACT',
    ...overrides,
  };
}

/** A valid VERIFY_EMAIL OtpEmailRequest, as user-service sends it at registration (#344). */
export function verifyEmailRequest(
  overrides: Partial<VerifyEmailOtpRequest> = {},
): VerifyEmailOtpRequest {
  return {
    requestId: '3f2a9d47-6c1b-4e8a-b5d0-7a9e1c2b3d4f',
    recipientUserId: '5b9e2f10-3c4d-4a6b-8e7f-9a0b1c2d3e4f',
    recipientEmail: 'newfarmer@test.com',
    otpCode: '730158',
    expiresAt: '2026-11-04T10:15:00Z',
    purpose: 'VERIFY_EMAIL',
    ...overrides,
  };
}

/** The notification_logs key (schema V2, #115) of the OTP email for `request`. */
export function otpKey(request: OtpEmailRequest): NotificationKey {
  return {
    eventId: request.requestId,
    recipientEmail: request.recipientEmail,
    notificationType:
      OTP_EMAIL_TEMPLATES[request.purpose].kind.notificationType,
  };
}
