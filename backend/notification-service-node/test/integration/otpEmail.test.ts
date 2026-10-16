import net from 'node:net';
import path from 'node:path';
import mysql from 'mysql2/promise';
import type { FastifyInstance, LightMyRequestResponse } from 'fastify';
import {
  GenericContainer,
  Network,
  Wait,
  type StartedNetwork,
  type StartedTestContainer,
} from 'testcontainers';
import { MySqlContainer, type StartedMySqlContainer } from '@testcontainers/mysql';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { OTP_TIME_BUDGET } from '../../src/application/otpTimeBudget.js';
import type { OtpEmailRequest } from '../../src/application/sendOtpEmail.js';
import { SendOtpEmail } from '../../src/application/sendOtpEmail.js';
import { SmtpEmailSender } from '../../src/infrastructure/email/smtpEmailSender.js';
import { renderOtpSignV1 } from '../../src/infrastructure/email/templates/otpSignV1.js';
import { buildApp } from '../../src/infrastructure/http/app.js';
import { OTP_EMAIL_PATH } from '../../src/infrastructure/http/otpEmailRoute.js';
import { NotificationLogRepository } from '../../src/infrastructure/persistence/notificationLogRepository.js';
import { LogCapture, otpRequest, SECRET } from '../support/otpFakes.js';

// Same versions as production: compose uses mysql:8.0, the Java service ships Flyway 10.10.0.
// mailhog/mailhog:latest in compose is v1.0.1.
const MYSQL_IMAGE = 'mysql:8.0';
const FLYWAY_IMAGE = 'flyway/flyway:10.10.0';
const MAILHOG_IMAGE = 'mailhog/mailhog:v1.0.1';
const DATABASE = 'notification_node_db';
const ROOT_PASSWORD = 'test';
const MIGRATIONS = path.resolve(import.meta.dirname, '../../db/migration');

interface MailHogMessage {
  ID: string;
  Content: { Headers: Record<string, string[] | undefined>; Body: string };
}

interface MailHogSearch {
  count: number;
  items: MailHogMessage[];
}

async function closedPort(): Promise<number> {
  const server = net.createServer();
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as net.AddressInfo;
  await new Promise<void>((resolve) => {
    server.close(() => {
      resolve();
    });
  });
  return port;
}

/** Polls until check() is true or timeoutMs passes; returns the last result. */
async function eventually(check: () => Promise<boolean>, timeoutMs: number): Promise<boolean> {
  const until = Date.now() + timeoutMs;
  for (;;) {
    if (await check()) return true;
    if (Date.now() >= until) return false;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
}

describe('OTP email endpoint against MySQL and MailHog', () => {
  let network: StartedNetwork;
  let db: StartedMySqlContainer;
  let mailhog: StartedTestContainer;
  let pool: mysql.Pool;
  const extraPools: mysql.Pool[] = [];
  let apps: FastifyInstance[] = [];
  let captures: LogCapture[] = [];
  /** Every OTP a test submitted; none may show up in logs or rows. */
  let submitted: string[] = [];

  function createPool(connectionLimit = 4): mysql.Pool {
    return mysql.createPool({
      host: db.getHost(),
      port: db.getPort(),
      database: DATABASE,
      user: 'root',
      password: ROOT_PASSWORD,
      connectionLimit,
    });
  }

  beforeAll(async () => {
    network = await new Network().start();
    [db, mailhog] = await Promise.all([
      new MySqlContainer(MYSQL_IMAGE)
        .withDatabase(DATABASE)
        .withRootPassword(ROOT_PASSWORD)
        .withNetwork(network)
        .withNetworkAliases('mysql')
        .start(),
      new GenericContainer(MAILHOG_IMAGE)
        .withExposedPorts(1025, 8025)
        .withWaitStrategy(Wait.forHttp('/api/v2/messages', 8025))
        .start(),
    ]);
    await new GenericContainer(FLYWAY_IMAGE)
      .withNetwork(network)
      .withCopyDirectoriesToContainer([{ source: MIGRATIONS, target: '/flyway/sql' }])
      .withCommand([
        `-url=jdbc:mysql://mysql:3306/${DATABASE}?allowPublicKeyRetrieval=true&useSSL=false`,
        '-user=root',
        `-password=${ROOT_PASSWORD}`,
        '-locations=filesystem:/flyway/sql',
        '-connectRetries=10',
        'migrate',
      ])
      .withWaitStrategy(Wait.forOneShotStartup())
      .start();
    pool = createPool();
  });

  afterAll(async () => {
    await Promise.all([pool, ...extraPools].map((p) => p.end()));
    await mailhog.stop();
    await db.stop();
    await network.stop();
  });

  afterEach(async () => {
    for (const app of apps) await app.close();
    // Boolean checks with labels, so a failure does not print the leaked value.
    for (const value of [...submitted, SECRET]) {
      for (const capture of captures) {
        expect(capture.contains(value), 'value found in a log line').toBe(false);
      }
      expect(await rowsContaining(value), 'value found in notification_logs').toBe(0);
    }
    apps = [];
    captures = [];
    submitted = [];
  });

  function start(options: { smtpPort?: number; pool?: mysql.Pool } = {}): FastifyInstance {
    const logs = new LogCapture();
    const app = buildApp(
      { database: () => Promise.resolve(true), rabbitmq: () => true },
      {
        otpEmail: {
          serviceInternalSecret: SECRET,
          sendOtpEmail: new SendOtpEmail({
            store: new NotificationLogRepository(options.pool ?? pool),
            sender: new SmtpEmailSender({
              host: mailhog.getHost(),
              port: options.smtpPort ?? mailhog.getMappedPort(1025),
              from: 'noreply@agricontract.com',
            }),
            render: renderOtpSignV1,
          }),
        },
        logStream: logs,
      },
    );
    apps.push(app);
    captures.push(logs);
    return app;
  }

  function newRequest(label: string): OtpEmailRequest {
    const request = otpRequest({ contractId: `ctr-it-${label}-${String(Date.now())}` });
    submitted.push(request.otpCode);
    return request;
  }

  function post(instance: FastifyInstance, body: unknown): Promise<LightMyRequestResponse> {
    return instance.inject({
      method: 'POST',
      url: OTP_EMAIL_PATH,
      headers: { 'content-type': 'application/json', 'x-internal-secret': SECRET },
      payload: JSON.stringify(body),
    });
  }

  async function mailsFor(contractId: string): Promise<MailHogSearch> {
    const url = new URL(
      '/api/v2/search',
      `http://${mailhog.getHost()}:${String(mailhog.getMappedPort(8025))}`,
    );
    url.searchParams.set('kind', 'containing');
    url.searchParams.set('query', contractId);
    const response = await fetch(url);
    expect(response.ok).toBe(true);
    return (await response.json()) as MailHogSearch;
  }

  async function rowsFor(requestId: string): Promise<mysql.RowDataPacket[]> {
    const [rows] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT status, user_id, recipient_email, event_type, notification_type, template_version,
              subject, body, provider_message_id, failure_reason, retry_count, sent_at
         FROM notification_logs WHERE event_id = ?`,
      [requestId],
    );
    return rows;
  }

  async function rowFor(requestId: string): Promise<mysql.RowDataPacket | undefined> {
    const rows = await rowsFor(requestId);
    expect(rows.length).toBeLessThanOrEqual(1);
    return rows[0];
  }

  async function rowsContaining(value: string): Promise<number> {
    const [rows] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT COUNT(*) AS n FROM notification_logs
        WHERE CONCAT_WS('|', event_id, user_id, recipient_email, subject, body,
                        IFNULL(provider_message_id, ''), IFNULL(failure_reason, '')) LIKE ?`,
      [`%${value}%`],
    );
    return Number(rows[0]?.n);
  }

  it('delivers through MailHog, records SENT and stores no code', async () => {
    const instance = start();
    const request = newRequest('sent');

    const response = await post(instance, request);

    expect(response.statusCode).toBe(200);
    expect(response.body).toBe('');
    const mails = await mailsFor(request.contractId);
    expect(mails.count).toBe(1);
    const mail = mails.items[0];
    expect(mail?.Content.Headers.To?.[0]).toBe(request.recipientEmail);
    expect(mail?.Content.Headers.Subject?.[0]).toBe('[AgriContract] Your contract signing code');
    expect(mail?.Content.Body.includes(request.otpCode), 'code missing from the mail').toBe(true);

    expect(await rowFor(request.requestId)).toMatchObject({
      status: 'SENT',
      user_id: request.recipientUserId,
      recipient_email: request.recipientEmail,
      event_type: 'otp.sign',
      notification_type: 'OTP_SIGN',
      template_version: 'OTP_SIGN_V1',
      body: '[redacted:OTP_SIGN_V1]',
      failure_reason: null,
      retry_count: 0,
    });
    expect((await rowFor(request.requestId))?.sent_at).not.toBeNull();
  });

  it('stores the id MailHog assigns (from "250 ... queued as <id>"), not our Message-ID', async () => {
    const instance = start();
    const request = newRequest('msgid');

    expect((await post(instance, request)).statusCode).toBe(200);

    const mail = (await mailsFor(request.contractId)).items[0];
    const stored = String((await rowFor(request.requestId))?.provider_message_id);
    const headerId = mail?.Content.Headers['Message-ID']?.[0];
    // Observed with MailHog v1.0.1: the queue id in the SMTP reply is the API message ID, and
    // it differs from the Message-ID header MailComposer generated.
    expect(stored).toBe(mail?.ID);
    expect(headerId).toBeDefined();
    expect(stored).not.toBe(headerId);
  });

  it('sends exactly one mail when the same request is repeated', async () => {
    const instance = start();
    const request = newRequest('repeat');

    const first = await post(instance, request);
    const second = await post(instance, request);

    expect([first.statusCode, second.statusCode]).toEqual([200, 200]);
    expect((await mailsFor(request.contractId)).count).toBe(1);
  });

  it('sends exactly one mail for concurrent requests across two service instances', async () => {
    // Two apps with their own pools behave like two replicas: only MySQL's unique index
    // serialises them.
    const replicaPool = createPool();
    extraPools.push(replicaPool);
    const replicas = [start(), start({ pool: replicaPool })];
    const request = newRequest('race');

    const responses = await Promise.all(
      Array.from({ length: 10 }, (_unused, i) => post(replicas[i % 2] as FastifyInstance, request)),
    );

    const outcomes = responses.map((r) =>
      r.statusCode === 200 ? 'OK' : r.json<{ code: string }>().code,
    );
    expect(outcomes).toContain('OK');
    expect(outcomes.every((o) => o === 'OK' || o === 'OTP_EMAIL_IN_PROGRESS')).toBe(true);
    expect((await mailsFor(request.contractId)).count).toBe(1);
    const rows = await rowsFor(request.requestId);
    expect(rows).toHaveLength(1);
    expect(rows[0]?.status).toBe('SENT');
  });

  it('answers 502 when SMTP is unreachable and never delivers that requestId later', async () => {
    const request = newRequest('closed');
    const broken = start({ smtpPort: await closedPort() });

    const response = await post(broken, request);

    expect(response.statusCode).toBe(502);
    expect(response.json<{ code: string }>().code).toBe('OTP_EMAIL_PROVIDER_FAILED');
    const row = await rowFor(request.requestId);
    expect(row).toMatchObject({ status: 'FAILED', retry_count: 1 });
    expect(String(row?.failure_reason)).toMatch(/^SMTP_E[A-Z]+$/);

    // Same requestId against a working SMTP server: still refused, still nothing sent.
    const retry = await post(start(), request);

    expect(retry.statusCode).toBe(502);
    expect(retry.json<{ code: string }>().code).toBe('OTP_EMAIL_PREVIOUSLY_FAILED');
    // Waiting for something not to happen: give a stray send time to reach MailHog.
    expect(await eventually(async () => (await mailsFor(request.contractId)).count > 0, 2000)).toBe(
      false,
    );
  });

  it('gives up without sending when the table is locked, within the request budget', async () => {
    const instance = start();
    const request = newRequest('locked');
    const locker = await pool.getConnection();
    await locker.query('LOCK TABLES notification_logs WRITE');
    let elapsed: number;
    let response: LightMyRequestResponse;
    try {
      const startedAt = Date.now();
      response = await post(instance, request);
      elapsed = Date.now() - startedAt;
    } finally {
      await locker.query('UNLOCK TABLES');
      locker.release();
    }

    expect(response.statusCode).toBe(502);
    expect(response.json<{ code: string }>().code).toBe('OTP_EMAIL_DEADLINE_EXCEEDED');
    expect(elapsed).toBeLessThan(OTP_TIME_BUDGET.requestBudgetMs);

    // The timed-out INSERT is not cancelled on the server; once the lock is gone it may still
    // commit. If it does, the row stays PENDING and nobody sends for it.
    await eventually(async () => (await rowsFor(request.requestId)).length > 0, 2000);
    const rows = await rowsFor(request.requestId);
    expect(rows.length).toBeLessThanOrEqual(1);
    if (rows.length === 1) {
      expect(rows[0]?.status).toBe('PENDING');
    }
    expect((await mailsFor(request.contractId)).count).toBe(0);
  });

  it('gives up when no pool connection frees up in time, and leaks no connection', async () => {
    const singlePool = createPool(1);
    extraPools.push(singlePool);
    const instance = start({ pool: singlePool });
    const held = await singlePool.getConnection();
    const blocked = newRequest('pool');
    let response: LightMyRequestResponse;
    try {
      response = await post(instance, blocked);
    } finally {
      held.release();
    }

    expect(response.statusCode).toBe(502);
    expect(response.json<{ code: string }>().code).toBe('OTP_EMAIL_DEADLINE_EXCEEDED');
    expect(await rowsFor(blocked.requestId)).toHaveLength(0);
    expect((await mailsFor(blocked.contractId)).count).toBe(0);

    // The timed-out waiter got the connection later and handed it back: the pool still works.
    const next = newRequest('pool-after');
    expect((await post(instance, next)).statusCode).toBe(200);
  });
});
