import { createTransport } from 'nodemailer';
import { otpRequest } from '../../../test/support/otp';
import {
  OTP_EMAIL_LOG_BODY,
  OTP_EMAIL_SUBJECT,
  renderOtpEmail,
} from './otp-email-template';

describe('renderOtpEmail', () => {
  const request = otpRequest({ expiresAt: '2026-11-04T10:15:00Z' });
  const { subject, body } = renderOtpEmail(request);
  const lines = body.split('\n');

  it('keeps the code out of the subject', () => {
    expect(subject).toBe(OTP_EMAIL_SUBJECT);
    expect(subject).not.toContain(request.otpCode);
  });

  it('puts the code on a line of its own, then the contract and the full hash', () => {
    expect(lines).toContain(request.otpCode);
    expect(lines).toContain('Contract: ctr-otp-1');
    expect(lines).toContain(request.signedContentHash);
  });

  it('shows the expiry in Vietnam time and in UTC', () => {
    expect(lines).toContain(
      'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
    );
  });

  it('converts an expiry sent with an offset', () => {
    expect(
      renderOtpEmail(otpRequest({ expiresAt: '2026-11-04T23:30:00+07:00' }))
        .body,
    ).toContain(
      'Expires: 2026-11-04 23:30:00 Vietnam time (2026-11-04T16:30:00.000Z UTC)',
    );
  });

  it('keeps every line within 76 characters for a 66-character contract id', () => {
    const long = renderOtpEmail(
      otpRequest({
        contractId: 'c'.repeat(66),
        expiresAt: '2026-12-31T23:59:59.999Z',
      }),
    ).body;
    for (const line of long.split('\n')) {
      expect(line.length).toBeLessThanOrEqual(76);
    }
  });

  it('is sent by nodemailer as 7bit, so the raw message holds the code and hash unbroken', async () => {
    const contractId = 'otp-it-8b6f3c1e-2a4d-4f7b-9c1e-0d2a3b4c5d6e';
    const mail = renderOtpEmail(otpRequest({ contractId }));
    const info = await createTransport({
      streamTransport: true,
      buffer: true,
    }).sendMail({
      from: 'noreply@agricontract.com',
      to: 'buyer1@test.com',
      subject: mail.subject,
      text: mail.body,
    });
    // Normalized: the stream transport's newline style is not under test.
    const raw = (info.message as Buffer)
      .toString('utf8')
      .replace(/\r\n/g, '\n');

    expect(raw).toContain('Content-Transfer-Encoding: 7bit');
    expect(raw).toContain(`\n${request.otpCode}\n`);
    expect(raw).toContain(`\nContract: ${contractId}\n`);
    expect(raw).toContain(`\n${request.signedContentHash}\n`);
  });

  it('stores a placeholder body that holds no code', () => {
    expect(OTP_EMAIL_LOG_BODY).not.toMatch(/\d{6}/);
  });
});
