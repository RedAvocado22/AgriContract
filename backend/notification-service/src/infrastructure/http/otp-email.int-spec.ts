/**
 * Integration test of POST /internal/v1/notifications/otp-email against a real
 * MySQL (V2 notification_logs) and a real MailHog, run with
 *   npm run test:int
 * DB_*, MAIL_HOST and MAIL_PORT apply with the usual defaults (localhost:3311,
 * localhost:1025); MAILHOG_API_URL defaults to http://localhost:8025.
 * This is the MailHog evidence of #114.
 */
import { randomUUID } from 'node:crypto';
import {
  createServer,
  type AddressInfo,
  type Server,
  type Socket,
} from 'node:net';
import { Logger, type INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import request from 'supertest';
import type { App } from 'supertest/types';
import { otpRequest } from '../../../test/support/otp';
import { APP_OPTIONS } from '../../app.options';
import {
  OTP_EMAIL_LOG_BODY,
  OTP_EMAIL_TEMPLATE_VERSION,
} from '../../application/otp/otp-email-template';
import { validate } from '../../config/env.validation';
import { OtpEmailModule } from '../../otp-email.module';

const PATH = '/internal/v1/notifications/otp-email';
const SECRET = 'int-test-internal-secret';
const MAILHOG = process.env.MAILHOG_API_URL ?? 'http://localhost:8025';
const db = validate(process.env).db;

interface MailHogMessage {
  ID: string;
  Content: { Headers: { To: string[]; Subject: string[] }; Body: string };
}

async function mailsContaining(text: string): Promise<MailHogMessage[]> {
  const res = await fetch(
    `${MAILHOG}/api/v2/search?kind=containing&query=${encodeURIComponent(text)}`,
  );
  return ((await res.json()) as { items: MailHogMessage[] }).items;
}

async function startApp(env: Record<string, string> = {}) {
  const moduleRef = await Test.createTestingModule({
    imports: [
      ConfigModule.forRoot({
        isGlobal: true,
        ignoreEnvFile: true,
        validate: () =>
          validate({
            ...process.env,
            SERVICE_INTERNAL_SECRET: SECRET,
            ...env,
          }),
      }),
      OtpEmailModule,
    ],
  }).compile();
  const app = moduleRef.createNestApplication(APP_OPTIONS);
  await app.init();
  return app;
}

describe('OTP email endpoint on real MySQL and MailHog', () => {
  let pool: Pool;
  let app: INestApplication;
  const requestIds: string[] = [];
  const mailIds: string[] = [];

  const newRequest = () => {
    const requestId = randomUUID();
    requestIds.push(requestId);
    return otpRequest({ requestId, contractId: `otp-it-${requestId}` });
  };
  const rowOf = async (requestId: string) => {
    const [rows] = await pool.query<RowDataPacket[]>(
      `SELECT status, retry_count, subject, body, recipient_user_id, template_version
         FROM notification_logs WHERE event_id = ?`,
      [requestId],
    );
    return rows;
  };
  const post = (target: INestApplication, body: object) =>
    request(target.getHttpServer() as App)
      .post(PATH)
      .set('X-Internal-Secret', SECRET)
      .send(body);

  beforeAll(async () => {
    pool = createPool({
      host: db.host,
      port: db.port,
      database: db.name,
      user: db.username,
      password: db.password,
      timezone: 'Z',
    });
    app = await startApp();
  });
  beforeEach(() => {
    for (const level of ['log', 'warn', 'error', 'debug'] as const) {
      jest.spyOn(Logger.prototype, level).mockImplementation(() => undefined);
    }
  });
  afterEach(() => jest.restoreAllMocks());
  afterAll(async () => {
    await app?.close();
    if (requestIds.length > 0) {
      await pool.query('DELETE FROM notification_logs WHERE event_id IN (?)', [
        requestIds,
      ]);
    }
    await pool.end();
    for (const id of mailIds) {
      await fetch(`${MAILHOG}/api/v1/messages/${id}`, { method: 'DELETE' });
    }
  });

  it('delivers one email to MailHog and records it SENT without the code', async () => {
    const body = newRequest();

    await post(app, body).expect(200);

    const mails = await mailsContaining(body.contractId);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.To).toEqual([body.recipientEmail]);
    expect(mails[0].Content.Headers.Subject).toEqual([
      '[AgriContract] Your contract signing code',
    ]);
    expect(mails[0].Content.Body).toContain(body.otpCode);
    expect(mails[0].Content.Body).toContain(body.signedContentHash);

    const rows = await rowOf(body.requestId);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      status: 'SENT',
      body: OTP_EMAIL_LOG_BODY,
      // The platform id of the recipient, as the caller sent it (#114, design §6),
      // and the template that rendered the mail.
      recipient_user_id: body.recipientUserId,
      template_version: OTP_EMAIL_TEMPLATE_VERSION,
    });
    expect(JSON.stringify(rows[0])).not.toContain(body.otpCode);
  });

  it('answers 200 to a repeated requestId without a second email', async () => {
    const body = newRequest();
    await post(app, body).expect(200);

    await post(app, body).expect(200);

    const mails = await mailsContaining(body.contractId);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
  });

  describe('with an SMTP server that never answers', () => {
    let silent: Server;
    let silentApp: INestApplication;
    const sockets = new Set<Socket>();

    beforeAll(async () => {
      // Accepts connections and never sends the SMTP greeting.
      silent = createServer((socket) => {
        sockets.add(socket);
        socket.on('close', () => sockets.delete(socket));
      });
      await new Promise<void>((resolve) =>
        silent.listen(0, '127.0.0.1', resolve),
      );
      silentApp = await startApp({
        MAIL_HOST: '127.0.0.1',
        MAIL_PORT: String((silent.address() as AddressInfo).port),
      });
    });
    afterAll(async () => {
      await silentApp?.close();
      for (const socket of sockets) socket.destroy();
      await new Promise((resolve) => silent.close(resolve));
    });

    it('answers 502 before the caller read timeout of 5s and records FAILED', async () => {
      const body = newRequest();
      const started = performance.now();

      const res = await post(silentApp, body);

      expect(res.status).toBe(502);
      expect(performance.now() - started).toBeLessThan(5_000);
      expect((res.body as { code: string }).code).toBe(
        'OTP_EMAIL_PROVIDER_FAILED',
      );
      const rows = await rowOf(body.requestId);
      expect(rows[0]).toMatchObject({ status: 'FAILED', retry_count: 1 });
    });
  });
});
