import { createTransport } from 'nodemailer';
import { otpRequest, verifyEmailRequest } from '../../../test/support/otp';
import { CONTRACT_OTP_PURPOSES } from './otp-email-request';
import {
  ACTIVATION_CANCEL_OTP_LOG_BODY,
  ACTIVATION_CANCEL_OTP_SUBJECT,
  CONCEDE_DISPUTE_OTP_LOG_BODY,
  CONCEDE_DISPUTE_OTP_SUBJECT,
  OTP_EMAIL_LOG_BODY,
  OTP_EMAIL_SUBJECT,
  SETTLE_DISPUTE_OTP_LOG_BODY,
  SETTLE_DISPUTE_OTP_SUBJECT,
  VERIFY_EMAIL_OTP_LOG_BODY,
  VERIFY_EMAIL_OTP_SUBJECT,
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

  /*
   * otp-email/v1 is stored in rows already written: adding a purpose must not
   * change a byte of the signing email.
   */
  it('renders the signing email exactly as otp-email/v1 always has', () => {
    expect(body).toBe(
      [
        'Use this one-time code to sign your AgriContract contract:',
        '',
        '482913',
        '',
        'Contract: ctr-otp-1',
        'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
        '',
        'Before you enter the code, check that the contract fingerprint',
        '(SHA-256) shown on screen is:',
        'a'.repeat(64),
        '',
        'Do not share this code with anyone. AgriContract staff will never',
        'ask for it. If you did not ask to sign this contract, ignore this',
        'email.',
      ].join('\n'),
    );
  });

  describe('purpose CANCEL_ACTIVATION', () => {
    const cancel = otpRequest({ purpose: 'CANCEL_ACTIVATION' });
    const mail = renderOtpEmail(cancel);
    const cancelLines = mail.body.split('\n');

    it('has its own subject, without the code', () => {
      expect(mail.subject).toBe(ACTIVATION_CANCEL_OTP_SUBJECT);
      expect(mail.subject).not.toBe(OTP_EMAIL_SUBJECT);
      expect(mail.subject).not.toContain(cancel.otpCode);
    });

    it('says the code confirms a cancel request, not a signature', () => {
      expect(mail.body).toContain('confirm your request to cancel');
      expect(mail.body).toContain('If you did not ask to cancel');
      expect(mail.body).not.toContain('to sign');
    });

    it('carries the code, the contract, the expiry and the full hash', () => {
      expect(cancelLines).toContain(cancel.otpCode);
      expect(cancelLines).toContain('Contract: ctr-otp-1');
      expect(cancelLines).toContain(
        'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
      );
      expect(cancelLines).toContain(cancel.signedContentHash);
    });

    it('stores its own placeholder body, which holds no code', () => {
      expect(ACTIVATION_CANCEL_OTP_LOG_BODY).toContain(
        'otp-activation-cancel/v1',
      );
      expect(ACTIVATION_CANCEL_OTP_LOG_BODY).not.toMatch(/\d{6}/);
    });
  });

  /*
   * #493 (D57): the whole of otp-concede-dispute/v1 and otp-settle-dispute/v1,
   * pinned so a later edit is a new version, not a silent change.
   */
  describe('purpose CONCEDE_DISPUTE', () => {
    const concede = otpRequest({ purpose: 'CONCEDE_DISPUTE' });
    const mail = renderOtpEmail(concede);

    it('has its own subject, without the code', () => {
      expect(mail.subject).toBe(CONCEDE_DISPUTE_OTP_SUBJECT);
      expect(mail.subject).not.toContain(concede.otpCode);
    });

    it('renders otp-concede-dispute/v1 exactly', () => {
      expect(mail.body).toBe(
        [
          "Use this one-time code to confirm that you accept the other party's",
          'position in the dispute on a milestone of your AgriContract contract.',
          '',
          '482913',
          '',
          'Contract: ctr-otp-1',
          'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
          '',
          'Before you enter the code, check that the contract fingerprint',
          '(SHA-256) shown on screen is:',
          'a'.repeat(64),
          '',
          "Entering this code ends the dispute on the other party's position.",
          'Nothing changes before you enter it.',
          '',
          'Do not share this code with anyone. AgriContract staff will never',
          'ask for it. If you did not ask to concede this dispute, ignore this',
          'email: nothing changes without the code.',
        ].join('\n'),
      );
    });

    it('stores its own placeholder body, which holds no code', () => {
      expect(CONCEDE_DISPUTE_OTP_LOG_BODY).toContain('otp-concede-dispute/v1');
      expect(CONCEDE_DISPUTE_OTP_LOG_BODY).not.toMatch(/\d{6}/);
    });
  });

  describe('purpose SETTLE_DISPUTE', () => {
    const settle = otpRequest({ purpose: 'SETTLE_DISPUTE' });
    const mail = renderOtpEmail(settle);

    it('has its own subject, without the code', () => {
      expect(mail.subject).toBe(SETTLE_DISPUTE_OTP_SUBJECT);
      expect(mail.subject).not.toBe(CONCEDE_DISPUTE_OTP_SUBJECT);
      expect(mail.subject).not.toContain(settle.otpCode);
    });

    it('renders otp-settle-dispute/v1 exactly', () => {
      expect(mail.body).toBe(
        [
          'Use this one-time code to confirm the settlement amount you proposed',
          'or accepted in the dispute on a milestone of your AgriContract',
          'contract.',
          '',
          '482913',
          '',
          'Contract: ctr-otp-1',
          'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
          '',
          'Before you enter the code, check that the contract fingerprint',
          '(SHA-256) shown on screen is:',
          'a'.repeat(64),
          '',
          'Nothing is recorded before you enter this code. A proposal then waits',
          'for the other party; an acceptance ends the dispute on the agreed',
          'amount.',
          '',
          'Do not share this code with anyone. AgriContract staff will never',
          'ask for it. If you did not propose or accept a settlement, ignore',
          'this email: nothing changes without the code.',
        ].join('\n'),
      );
    });

    it('stores its own placeholder body, which holds no code', () => {
      expect(SETTLE_DISPUTE_OTP_LOG_BODY).toContain('otp-settle-dispute/v1');
      expect(SETTLE_DISPUTE_OTP_LOG_BODY).not.toMatch(/\d{6}/);
    });
  });

  describe('purpose VERIFY_EMAIL (#344)', () => {
    const verify = verifyEmailRequest();
    const mail = renderOtpEmail(verify);

    it('has its own subject, without the code', () => {
      expect(mail.subject).toBe(VERIFY_EMAIL_OTP_SUBJECT);
      expect(mail.subject).not.toContain(verify.otpCode);
    });

    /*
     * The whole of otp-verify-email/v1: the code, its expiry in Vietnam time
     * and UTC, and what to do if you did not register. No contract wording.
     */
    it('renders otp-verify-email/v1 exactly', () => {
      expect(mail.body).toBe(
        [
          'Use this one-time code to verify the email address of your',
          'AgriContract account:',
          '',
          '730158',
          '',
          'Expires: 2026-11-04 17:15:00 Vietnam time (2026-11-04T10:15:00.000Z UTC)',
          '',
          'Do not share this code with anyone. AgriContract staff will never',
          'ask for it. If you did not register on AgriContract, ignore this',
          'email: the address is not verified without the code.',
        ].join('\n'),
      );
    });

    it('says nothing about a contract, a signature or a fingerprint', () => {
      // \b: "AgriContract" is the product name, not a contract.
      expect(mail.body).not.toMatch(/\bcontract|\bsign|fingerprint|SHA-256/i);
      expect(mail.subject).not.toMatch(/\bcontract/i);
    });

    it('stores its own placeholder body, which holds no code', () => {
      expect(VERIFY_EMAIL_OTP_LOG_BODY).toContain('otp-verify-email/v1');
      expect(VERIFY_EMAIL_OTP_LOG_BODY).not.toMatch(/\d{6}/);
    });

    it('keeps every line within 76 characters', () => {
      const long = renderOtpEmail(
        verifyEmailRequest({ expiresAt: '2026-12-31T23:59:59.999Z' }),
      ).body;
      for (const line of long.split('\n')) {
        expect(line.length).toBeLessThanOrEqual(76);
      }
    });
  });

  it.each(CONTRACT_OTP_PURPOSES)(
    'keeps every %s line within 76 characters for a 66-character contract id',
    (purpose) => {
      const long = renderOtpEmail(
        otpRequest({
          purpose,
          contractId: 'c'.repeat(66),
          expiresAt: '2026-12-31T23:59:59.999Z',
        }),
      ).body;
      for (const line of long.split('\n')) {
        expect(line.length).toBeLessThanOrEqual(76);
      }
    },
  );

  it.each(CONTRACT_OTP_PURPOSES)(
    'sends the %s email as 7bit, so the raw message holds the code and hash unbroken',
    async (purpose) => {
      const contractId = 'otp-it-8b6f3c1e-2a4d-4f7b-9c1e-0d2a3b4c5d6e';
      const mail = renderOtpEmail(otpRequest({ contractId, purpose }));
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
    },
  );

  it('stores a placeholder body that holds no code', () => {
    expect(OTP_EMAIL_LOG_BODY).not.toMatch(/\d{6}/);
  });
});
