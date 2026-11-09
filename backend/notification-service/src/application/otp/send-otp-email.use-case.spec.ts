import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import { otpKey, otpRequest } from '../../../test/support/otp';
import { NotificationStatus } from '../../domain/notification-log';
import { CLAIM_LEASE_MS } from '../notification-sender';
import {
  ACTIVATION_CANCEL_OTP_KIND,
  ACTIVATION_CANCEL_OTP_LOG_BODY,
  ACTIVATION_CANCEL_OTP_SUBJECT,
  OTP_EMAIL_KIND,
  OTP_EMAIL_LOG_BODY,
  OTP_EMAIL_SUBJECT,
  OTP_EMAIL_TEMPLATE_VERSION,
} from './otp-email-template';
import {
  OtpEmailDeliveryError,
  OtpEmailInProgressError,
  OtpEmailLogUnavailableError,
  SendOtpEmailUseCase,
} from './send-otp-email.use-case';

describe('SendOtpEmailUseCase', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let logger: { error: jest.Mock };
  let useCase: SendOtpEmailUseCase;
  const request = otpRequest();
  const row = () => logs.get(otpKey(request));

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    logger = { error: jest.fn() };
    useCase = new SendOtpEmailUseCase(logs, email, logger);
  });

  it('sends the rendered email and records it SENT without the code', async () => {
    await expect(useCase.execute(request)).resolves.toBe('SENT');

    expect(email.sent).toHaveLength(1);
    expect(email.sent[0].to).toBe(request.recipientEmail);
    expect(email.sent[0].subject).toBe(OTP_EMAIL_SUBJECT);
    expect(email.sent[0].body).toContain(request.otpCode);
    expect(row()).toMatchObject({
      eventId: request.requestId,
      eventType: OTP_EMAIL_KIND.eventType,
      notificationType: OTP_EMAIL_KIND.notificationType,
      recipientUserId: request.recipientUserId,
      recipientEmail: request.recipientEmail,
      status: NotificationStatus.SENT,
      subject: OTP_EMAIL_SUBJECT,
      body: OTP_EMAIL_LOG_BODY,
      templateVersion: OTP_EMAIL_TEMPLATE_VERSION,
    });
    expect(JSON.stringify(row())).not.toContain(request.otpCode);
  });

  it('sends a CANCEL_ACTIVATION code with its own template and records it under its own type', async () => {
    const cancel = otpRequest({ purpose: 'CANCEL_ACTIVATION' });

    await expect(useCase.execute(cancel)).resolves.toBe('SENT');

    expect(email.sent).toHaveLength(1);
    expect(email.sent[0].subject).toBe(ACTIVATION_CANCEL_OTP_SUBJECT);
    expect(email.sent[0].body).toContain(cancel.otpCode);
    expect(email.sent[0].body).toContain('confirm your request to cancel');
    expect(logs.get(otpKey(cancel))).toMatchObject({
      eventId: cancel.requestId,
      eventType: ACTIVATION_CANCEL_OTP_KIND.eventType,
      notificationType: 'ACTIVATION_CANCEL_OTP',
      status: NotificationStatus.SENT,
      subject: ACTIVATION_CANCEL_OTP_SUBJECT,
      body: ACTIVATION_CANCEL_OTP_LOG_BODY,
      templateVersion: 'otp-activation-cancel/v1',
    });
    expect(logs.get(otpKey(request))).toBeUndefined();
    expect(JSON.stringify(logs.get(otpKey(cancel)))).not.toContain(
      cancel.otpCode,
    );
  });

  it('does not send again for a requestId already SENT', async () => {
    await useCase.execute(request);

    await expect(useCase.execute(request)).resolves.toBe('ALREADY_SENT');
    expect(email.sent).toHaveLength(1);
  });

  it('fails with a code-only error and records FAILED when the provider rejects', async () => {
    email.failFor.add(request.recipientEmail);

    const error = await useCase.execute(request).catch((err: unknown) => err);

    expect(error).toBeInstanceOf(OtpEmailDeliveryError);
    expect((error as Error).message).toBe(
      `OTP request ${request.requestId}: email delivery failed (EENVELOPE/550)`,
    );
    expect(row()).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });
  });

  it('sends again when the caller repeats a requestId that FAILED', async () => {
    email.failFor.add(request.recipientEmail);
    await useCase.execute(request).catch(() => undefined);
    email.failFor.clear();

    await expect(useCase.execute(request)).resolves.toBe('SENT');
    expect(email.sent).toHaveLength(1);
    expect(row()?.status).toBe(NotificationStatus.SENT);
  });

  it('returns at once with OtpEmailInProgressError while another call holds a live claim', async () => {
    logs.seed(otpKey(request), NotificationStatus.PENDING);

    await expect(useCase.execute(request)).rejects.toBeInstanceOf(
      OtpEmailInProgressError,
    );
    expect(email.sent).toHaveLength(0);
  });

  it('takes over a claim older than the lease (the caller died mid-send)', async () => {
    logs.seed(otpKey(request), NotificationStatus.PENDING, 0);
    logs.now = CLAIM_LEASE_MS + 1;

    await expect(useCase.execute(request)).resolves.toBe('SENT');
    expect(email.sent).toHaveLength(1);
  });

  it('sends nothing when the log cannot be claimed', async () => {
    jest
      .spyOn(logs, 'claim')
      .mockRejectedValue(
        Object.assign(new Error('buyer1@test.com'), { code: 'ECONNREFUSED' }),
      );

    const error = await useCase.execute(request).catch((err: unknown) => err);

    expect(error).toBeInstanceOf(OtpEmailLogUnavailableError);
    expect((error as Error).message).toBe(
      `OTP request ${request.requestId}: notification log unavailable (ECONNREFUSED)`,
    );
    expect(email.sent).toHaveLength(0);
  });

  it('still succeeds when SENT cannot be recorded, and logs only a code', async () => {
    jest
      .spyOn(logs, 'markSent')
      .mockRejectedValue(
        Object.assign(new Error('buyer1@test.com'), { code: 'ER_LOCK_WAIT' }),
      );

    await expect(useCase.execute(request)).resolves.toBe('SENT');
    expect(logger.error).toHaveBeenCalledWith(
      `OTP request ${request.requestId}: email sent but could not record SENT (ER_LOCK_WAIT)`,
    );
  });

  it('still reports the provider failure when FAILED cannot be recorded', async () => {
    email.failFor.add(request.recipientEmail);
    jest
      .spyOn(logs, 'markFailed')
      .mockRejectedValue(Object.assign(new Error('x'), { code: 'ER_X' }));

    await expect(useCase.execute(request)).rejects.toBeInstanceOf(
      OtpEmailDeliveryError,
    );
    expect(logger.error).toHaveBeenCalledWith(
      `OTP request ${request.requestId}: could not record FAILED (ER_X)`,
    );
  });
});
