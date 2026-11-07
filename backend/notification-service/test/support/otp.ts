import type { OtpEmailRequest } from '../../src/application/otp/otp-email-request';
import { OTP_EMAIL_KIND } from '../../src/application/otp/otp-email-template';
import type { NotificationKey } from '../../src/domain/notification-log';

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

/** The notification_logs key (schema V2, #115) of the OTP email for `request`. */
export function otpKey(request: OtpEmailRequest): NotificationKey {
  return {
    eventId: request.requestId,
    recipientEmail: request.recipientEmail,
    notificationType: OTP_EMAIL_KIND.notificationType,
  };
}
