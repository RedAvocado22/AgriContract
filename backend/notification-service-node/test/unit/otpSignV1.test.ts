import { describe, expect, it } from 'vitest';
import {
  formatIct,
  OTP_SIGN_V1,
  renderOtpSignV1,
  shortHash,
} from '../../src/infrastructure/email/templates/otpSignV1.js';
import { otpRequest } from '../support/otpFakes.js';

const HASH = '0123456789abcdef'.repeat(4);

describe('OTP_SIGN_V1 template', () => {
  it('renders code, contract, short hash and ICT expiry in text and html', () => {
    const request = otpRequest({ signedContentHash: HASH, expiresAt: '2026-10-16T08:04:05.000Z' });
    const email = renderOtpSignV1(request);

    expect(email.templateVersion).toBe(OTP_SIGN_V1);
    for (const body of [email.text, email.html]) {
      expect(body.includes(request.otpCode), 'code missing from the body').toBe(true);
      expect(body).toContain('ctr-2026-0001');
      expect(body).toContain('01234567...89abcdef');
      expect(body).toContain('16/10/2026, 15:04:05 ICT (UTC+07:00)');
      expect(body).not.toContain(HASH);
    }
  });

  it('keeps the code out of the subject, which is persisted', () => {
    expect(renderOtpSignV1(otpRequest()).subject).not.toMatch(/\d{6}/);
  });

  it('escapes dynamic values in html', () => {
    const email = renderOtpSignV1(otpRequest({ contractId: '<img src=x onerror="alert(1)">&' }));

    expect(email.html).not.toContain('<img');
    expect(email.html).toContain('&lt;img src=x onerror=&quot;alert(1)&quot;&gt;&amp;');
  });

  it('formats instants in Asia/Ho_Chi_Minh across the UTC date line', () => {
    expect(formatIct(new Date('2026-12-31T17:30:00Z'))).toBe(
      '01/01/2027, 00:30:00 ICT (UTC+07:00)',
    );
  });

  it('shortens a hash to its first and last 8 characters', () => {
    expect(shortHash(HASH)).toBe('01234567...89abcdef');
  });
});
