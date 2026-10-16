import type { OtpEmailRequest, RenderedOtpEmail } from '../../../application/sendOtpEmail.js';

export const OTP_SIGN_V1 = 'OTP_SIGN_V1';

// Persisted in notification_logs.subject, so it must never contain the code.
const SUBJECT = '[AgriContract] Your contract signing code';

const ICT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** "16/10/2026, 15:04:05 ICT (UTC+07:00)" */
export function formatIct(instant: Date): string {
  return `${ICT.format(instant)} ICT (UTC+07:00)`;
}

/** First and last 8 hex characters, enough to compare with the hash shown in the app. */
export function shortHash(hash: string): string {
  return `${hash.slice(0, 8)}...${hash.slice(-8)}`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Renders in memory; the result holds the OTP and must not be persisted or logged. */
export function renderOtpSignV1(request: OtpEmailRequest): RenderedOtpEmail {
  const values = {
    code: request.otpCode,
    contractId: request.contractId,
    hash: shortHash(request.signedContentHash),
    expiresAt: formatIct(new Date(request.expiresAt)),
  };

  const text = [
    'Your AgriContract signing code is:',
    '',
    `    ${values.code}`,
    '',
    `Contract: ${values.contractId}`,
    `Content hash: ${values.hash}`,
    `Expires at: ${values.expiresAt}`,
    '',
    'Enter this code only if the contract and content hash match what you are signing.',
    'Never share this code. AgriContract staff will never ask for it.',
  ].join('\n');

  const html = [
    '<p>Your AgriContract signing code is:</p>',
    `<p style="font-size:24px;font-weight:bold;letter-spacing:4px">${escapeHtml(values.code)}</p>`,
    '<table>',
    `<tr><td>Contract</td><td>${escapeHtml(values.contractId)}</td></tr>`,
    `<tr><td>Content hash</td><td><code>${escapeHtml(values.hash)}</code></td></tr>`,
    `<tr><td>Expires at</td><td>${escapeHtml(values.expiresAt)}</td></tr>`,
    '</table>',
    '<p>Enter this code only if the contract and content hash match what you are signing.</p>',
    '<p>Never share this code. AgriContract staff will never ask for it.</p>',
  ].join('\n');

  return { templateVersion: OTP_SIGN_V1, subject: SUBJECT, text, html };
}
