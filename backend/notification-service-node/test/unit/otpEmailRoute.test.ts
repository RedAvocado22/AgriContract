import type { FastifyInstance, LightMyRequestResponse } from 'fastify';
import { afterEach, describe, expect, it } from 'vitest';
import { SendOtpEmail } from '../../src/application/sendOtpEmail.js';
import { renderOtpSignV1 } from '../../src/infrastructure/email/templates/otpSignV1.js';
import { buildApp } from '../../src/infrastructure/http/app.js';
import { OTP_EMAIL_PATH } from '../../src/infrastructure/http/otpEmailRoute.js';
import {
  deferred,
  deliveryError,
  FakeEmailSender,
  InMemoryNotificationLogStore,
  LogCapture,
  otpRequest,
  randomOtp,
  SECRET,
} from '../support/otpFakes.js';

const healthy = { database: () => Promise.resolve(true), rabbitmq: () => true };

describe(`POST ${OTP_EMAIL_PATH}`, () => {
  let app: FastifyInstance | undefined;
  let store: InMemoryNotificationLogStore;
  let sender: FakeEmailSender;
  let logs: LogCapture;

  function start(now?: () => Date): FastifyInstance {
    store = new InMemoryNotificationLogStore();
    sender = new FakeEmailSender();
    logs = new LogCapture();
    app = buildApp(healthy, {
      otpEmail: {
        serviceInternalSecret: SECRET,
        sendOtpEmail: new SendOtpEmail({ store, sender, render: renderOtpSignV1 }),
        ...(now === undefined ? {} : { now }),
      },
      logStream: logs,
    });
    return app;
  }

  function post(
    body: unknown,
    headers: Record<string, string> = { 'x-internal-secret': SECRET },
  ): Promise<LightMyRequestResponse> {
    const instance = app ?? start();
    return instance.inject({
      method: 'POST',
      url: OTP_EMAIL_PATH,
      headers: { 'content-type': 'application/json', ...headers },
      payload: typeof body === 'string' ? body : JSON.stringify(body),
    });
  }

  function expectEnvelope(response: LightMyRequestResponse, status: number, code: string): void {
    expect(response.statusCode).toBe(status);
    const body = response.json<Record<string, unknown>>();
    expect(body).toMatchObject({ status, code, path: OTP_EMAIL_PATH });
    expect(Object.keys(body).sort()).toEqual(
      ['code', 'error', 'message', 'path', 'status', 'timestamp', 'traceId'].sort(),
    );
  }

  /**
   * Nothing the service wrote, stored or answered may contain these values. Boolean checks
   * with labels, so a failure names where it leaked without printing the leaked content.
   */
  function expectNowhere(values: string[], response?: LightMyRequestResponse): void {
    for (const value of [...values, SECRET]) {
      expect(logs.contains(value), 'value found in a log line').toBe(false);
      expect(store.contains(value), 'value found in a stored row').toBe(false);
      if (response !== undefined) {
        expect(response.body.includes(value), 'value found in the response body').toBe(false);
      }
    }
  }

  afterEach(async () => {
    await app?.close();
    app = undefined;
  });

  describe('authentication', () => {
    it('returns 401 without X-Internal-Secret', async () => {
      start();
      const request = otpRequest();
      const response = await post(request, {});

      expectEnvelope(response, 401, 'UNAUTHORIZED');
      expect(sender.sent).toHaveLength(0);
      expect(store.rows.size).toBe(0);
      expectNowhere([request.otpCode], response);
    });

    it('returns 401 for an empty secret', async () => {
      start();
      const response = await post(otpRequest(), { 'x-internal-secret': '' });

      expectEnvelope(response, 401, 'UNAUTHORIZED');
    });

    it('returns 401 for a wrong secret of the same length and does not log it', async () => {
      start();
      const wrong = 'x'.repeat(SECRET.length);
      const response = await post(otpRequest(), { 'x-internal-secret': wrong });

      expectEnvelope(response, 401, 'UNAUTHORIZED');
      expectNowhere([wrong], response);
    });

    it.each([1, SECRET.length - 1, SECRET.length + 1, 4096])(
      'returns 401 without throwing for a %i-character secret',
      async (length) => {
        start();
        const response = await post(otpRequest(), { 'x-internal-secret': 'y'.repeat(length) });

        expectEnvelope(response, 401, 'UNAUTHORIZED');
      },
    );

    it('checks the secret before validating the body', async () => {
      start();
      const response = await post({ unexpected: true }, {});

      expectEnvelope(response, 401, 'UNAUTHORIZED');
    });
  });

  describe('validation', () => {
    const invalid: [string, Record<string, unknown>][] = [
      ['otpCode with letters', { otpCode: '12a456' }],
      ['otpCode with 5 digits', { otpCode: randomOtp().slice(0, 5) }],
      ['otpCode with 7 digits', { otpCode: `${randomOtp()}7` }],
      ['otpCode as a number', { otpCode: Number(randomOtp()) }],
      ['signedContentHash with 63 chars', { signedContentHash: 'a'.repeat(63) }],
      ['signedContentHash with non-hex chars', { signedContentHash: 'g'.repeat(64) }],
      ['signedContentHash in upper case', { signedContentHash: 'AB'.repeat(32) }],
      ['recipientEmail without @', { recipientEmail: 'not-an-email' }],
      ['expiresAt that is not a date-time', { expiresAt: 'tomorrow' }],
      ['expiresAt without a zone', { expiresAt: '2099-01-01T00:00:00' }],
      ['expiresAt in the past', { expiresAt: new Date(Date.now() - 60_000).toISOString() }],
      ['requestId longer than event_id', { requestId: 'r'.repeat(37) }],
      ['empty requestId', { requestId: '' }],
      ['empty recipientUserId', { recipientUserId: '' }],
      ['recipientUserId longer than user_id', { recipientUserId: 'u'.repeat(256) }],
      ['recipientEmail longer than recipient_email', { recipientEmail: `${'a'.repeat(251)}@x.io` }],
      ['an unknown extra field', { extra: 'x' }],
    ];

    it.each(invalid)('returns 400 for %s', async (_name, change) => {
      start();
      const request = otpRequest();
      const changed = change.otpCode;
      // The exact code that was submitted, valid or not.
      const submitted =
        typeof changed === 'string'
          ? changed
          : typeof changed === 'number'
            ? changed.toString()
            : request.otpCode;

      const response = await post({ ...request, ...change });

      expectEnvelope(response, 400, 'VALIDATION_ERROR');
      expect(sender.sent).toHaveLength(0);
      expect(store.rows.size).toBe(0);
      expectNowhere([submitted], response);
    });

    it.each([
      'requestId',
      'recipientUserId',
      'recipientEmail',
      'contractId',
      'otpCode',
      'signedContentHash',
      'expiresAt',
    ])('returns 400 when %s is missing', async (field) => {
      start();
      const request = otpRequest();
      const body = Object.fromEntries(Object.entries(request).filter(([name]) => name !== field));

      const response = await post(body);

      expectEnvelope(response, 400, 'VALIDATION_ERROR');
      expect(response.json<{ message: string }>().message).toContain(field);
      expect(sender.sent).toHaveLength(0);
      expectNowhere([request.otpCode], response);
    });

    it.each([
      ['truncated', (otp: string) => `{"otpCode":"${otp}",`],
      ['unquoted key', (otp: string) => `{otpCode:"${otp}"}`],
      ['trailing garbage', (otp: string) => `${JSON.stringify(otpRequest({ otpCode: otp }))}x`],
    ])(
      'returns 400 for malformed JSON (%s) without logging or echoing the code',
      async (_n, make) => {
        start();
        const otp = randomOtp();

        const response = await post(make(otp));

        expectEnvelope(response, 400, 'MALFORMED_REQUEST');
        expectNowhere([otp], response);
      },
    );

    it('answers 400, not 413, for a body over 4096 bytes', async () => {
      start();
      const request = otpRequest({ contractId: 'c'.repeat(5000) });

      const response = await post(request);

      expectEnvelope(response, 400, 'MALFORMED_REQUEST');
      expect(sender.sent).toHaveLength(0);
      expectNowhere([request.otpCode], response);
    });

    // Fastify parses text/plain itself (the schema then rejects a string body); types it has no
    // parser for raise 415, which the route turns into 400.
    it.each([
      ['application/xml', 'MALFORMED_REQUEST'],
      ['text/plain', 'VALIDATION_ERROR'],
    ])('answers 400, not 415, for a %s body', async (contentType, code) => {
      const instance = start();
      const request = otpRequest();

      const response = await instance.inject({
        method: 'POST',
        url: OTP_EMAIL_PATH,
        headers: { 'content-type': contentType, 'x-internal-secret': SECRET },
        payload: JSON.stringify(request),
      });

      expectEnvelope(response, 400, code);
      expect(sender.sent).toHaveLength(0);
      expectNowhere([request.otpCode], response);
    });

    it('rejects expiresAt equal to now and accepts one millisecond later', async () => {
      const now = new Date('2099-01-01T00:00:00.000Z');
      start(() => now);

      const atNow = await post(otpRequest({ expiresAt: now.toISOString() }));
      const justAfter = await post(otpRequest({ expiresAt: '2099-01-01T00:00:00.001Z' }));

      expectEnvelope(atNow, 400, 'VALIDATION_ERROR');
      expect(atNow.json<{ message: string }>().message).toBe('expiresAt must be in the future');
      expect(justAfter.statusCode).toBe(200);
    });

    it('accepts expiresAt with an offset', async () => {
      start();
      const response = await post(otpRequest({ expiresAt: '2099-01-01T07:00:00+07:00' }));

      expect(response.statusCode).toBe(200);
    });
  });

  describe('idempotency', () => {
    it('sends once, records SENT and answers 200 with no body', async () => {
      start();
      const request = otpRequest();

      const response = await post(request);

      expect(response.statusCode).toBe(200);
      expect(response.body).toBe('');
      expect(sender.sent).toHaveLength(1);
      expect(sender.sent[0]?.to).toBe(request.recipientEmail);
      expect(sender.sent[0]?.text.includes(request.otpCode), 'code missing from the mail').toBe(
        true,
      );
      const [row] = store.rows.values();
      expect(row).toMatchObject({
        eventId: request.requestId,
        eventType: 'otp.sign',
        notificationType: 'OTP_SIGN',
        recipientUserId: request.recipientUserId,
        recipientEmail: request.recipientEmail,
        templateVersion: 'OTP_SIGN_V1',
        body: '[redacted:OTP_SIGN_V1]',
        status: 'SENT',
        providerMessageId: '<1@fake>',
      });
      expect(row?.sentAt).toBeInstanceOf(Date);
      expectNowhere([request.otpCode], response);
    });

    it('answers 200 for an existing SENT row without sending', async () => {
      start();
      const request = otpRequest();
      store.seed(request, 'SENT');

      const response = await post(request);

      expect(response.statusCode).toBe(200);
      expect(sender.sent).toHaveLength(0);
    });

    it('answers 502 for an existing FAILED row without sending', async () => {
      start();
      const request = otpRequest();
      store.seed(request, 'FAILED');

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_PREVIOUSLY_FAILED');
      expect(sender.sent).toHaveLength(0);
    });

    it('answers 502 for an existing PENDING row without sending', async () => {
      start();
      const request = otpRequest();
      store.seed(request, 'PENDING');

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_IN_PROGRESS');
      expect(sender.sent).toHaveLength(0);
    });

    it('treats the same requestId for another recipient as a new key', async () => {
      start();
      const request = otpRequest();
      store.seed(request, 'FAILED');

      const response = await post({ ...request, recipientEmail: 'other@test.local' });

      expect(response.statusCode).toBe(200);
      expect(sender.sent).toHaveLength(1);
    });

    it('does not send again when the same request is repeated', async () => {
      start();
      const request = otpRequest();

      const first = await post(request);
      const second = await post(request);

      expect([first.statusCode, second.statusCode]).toEqual([200, 200]);
      expect(sender.sent).toHaveLength(1);
    });

    // Atomicity here comes from the in-memory fake; the MySQL unique index is covered by
    // test/integration/otpEmail.test.ts.
    it('lets only one of two concurrent requests send', async () => {
      start();
      const request = otpRequest();
      const gate = deferred();
      sender.gate = gate.promise;

      const first = post(request);
      const second = post(request);
      // The loser sees the winner's PENDING row and answers without waiting for the send.
      const loser = await Promise.race([first, second]);
      gate.resolve();
      const statuses = (await Promise.all([first, second])).map((r) => r.statusCode).sort();

      expect(loser.statusCode).toBe(502);
      expect(statuses).toEqual([200, 502]);
      expect(sender.sent).toHaveLength(1);
    });
  });

  describe('provider failure', () => {
    it('records FAILED with a safe reason and answers 502', async () => {
      start();
      sender.failWith = deliveryError('SMTP_ETIMEDOUT');
      const request = otpRequest();

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_PROVIDER_FAILED');
      const [row] = store.rows.values();
      expect(row).toMatchObject({
        status: 'FAILED',
        failureReason: 'SMTP_ETIMEDOUT',
        retryCount: 1,
      });
      expectNowhere([request.otpCode], response);
    });

    it('answers 502 DELIVERY_UNKNOWN when the provider may still deliver', async () => {
      start();
      sender.failWith = deliveryError('SMTP_DEADLINE_EXCEEDED_AFTER_DATA', 'UNKNOWN');
      const request = otpRequest();

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_DELIVERY_UNKNOWN');
      expect(response.json<{ message: string }>().message).toContain('may still deliver');
      expectNowhere([request.otpCode], response);
    });

    it('never sends a failed requestId again', async () => {
      start();
      sender.failWith = deliveryError('SMTP_ECONNECTION');
      const request = otpRequest();
      await post(request);
      sender.failWith = undefined;

      const retry = await post(request);

      expectEnvelope(retry, 502, 'OTP_EMAIL_PREVIOUSLY_FAILED');
      expect(sender.sent).toHaveLength(0);
    });

    it('stores a generic reason for an unexpected sender error', async () => {
      start();
      const request = otpRequest();
      sender.failWith = new Error(`provider said: code ${request.otpCode} rejected`);

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_DELIVERY_UNKNOWN');
      expect([...store.rows.values()][0]?.failureReason).toBe('SMTP_UNKNOWN_ERROR');
      expectNowhere([request.otpCode], response);
    });
  });

  describe('persistence failure', () => {
    it('answers 502, not 200, when SENT cannot be recorded after the provider accepted', async () => {
      start();
      const request = otpRequest();
      store.failOn.markSent = Object.assign(new Error('connection lost'), { code: 'ECONNRESET' });

      const response = await post(request);

      expectEnvelope(response, 502, 'OTP_EMAIL_RESULT_NOT_RECORDED');
      expect(sender.sent).toHaveLength(1);
      expect([...store.rows.values()][0]?.status).toBe('PENDING');
      expect(logs.contains('ECONNRESET')).toBe(true);
      expectNowhere([request.otpCode], response);
    });

    it('answers 502 when FAILED cannot be recorded', async () => {
      start();
      sender.failWith = deliveryError('SMTP_ETIMEDOUT');
      store.failOn.markFailed = new Error('connection lost');

      const response = await post(otpRequest());

      expectEnvelope(response, 502, 'OTP_EMAIL_PROVIDER_FAILED');
    });

    it('answers 500 without sending when the row cannot be reserved', async () => {
      start();
      const request = otpRequest();
      store.failOn.reserve = Object.assign(new Error(`INSERT ... '${request.otpCode}'`), {
        code: 'PROTOCOL_CONNECTION_LOST',
      });

      const response = await post(request);

      expectEnvelope(response, 500, 'INTERNAL_ERROR');
      expect(sender.sent).toHaveLength(0);
      expect(logs.contains('PROTOCOL_CONNECTION_LOST')).toBe(true);
      expectNowhere([request.otpCode], response);
    });
  });
});
