/**
 * Integration test of the KYC-result email
 * (`notification.user_kyc_result_requested`) against a real MySQL (V2
 * notification_logs) and a real MailHog, run with
 *   npm run test:int
 * DB_*, MAIL_HOST and MAIL_PORT apply with the usual defaults (localhost:3311,
 * localhost:1025); MAILHOG_API_URL defaults to http://localhost:8025.
 *
 * The command goes through the real parser, so the path from published bytes
 * to a stored row and a delivered email is the production one, minus RabbitMQ.
 */
import { ConfigService } from '@nestjs/config';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import { createTransport } from 'nodemailer';
import {
  KYC_USER_ID,
  kycBody,
  kycPayload,
  kycVerifiedPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseUserKycResultRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyUserKycResultUseCase } from './notify-user-kyc-result.use-case';

const config = validate(process.env);
const db = config.db;
const MAILHOG = process.env.MAILHOG_API_URL ?? 'http://localhost:8025';

interface MailHogMessage {
  ID: string;
  Content: {
    Headers: {
      Subject: string[];
      'Content-Transfer-Encoding'?: string[];
    };
    Body: string;
  };
}

async function mailsContaining(text: string): Promise<MailHogMessage[]> {
  const res = await fetch(
    `${MAILHOG}/api/v2/search?kind=containing&query=${encodeURIComponent(text)}`,
  );
  return ((await res.json()) as { items: MailHogMessage[] }).items;
}

describe('KYC-result email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyUserKycResultUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and address are unique to one test. */
  const command = (tag: string, payload = kycPayload()) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseUserKycResultRequested(
      kycBody({
        eventId,
        correlationId: eventId,
        payload: { ...payload, recipientEmail: `owner-${tag}@kyc.int.test` },
      }),
    );
  };

  const rowsOf = async (eventId: string) => {
    const [rows] = await pool.query<RowDataPacket[]>(
      `SELECT recipient_email, recipient_user_id, event_type, notification_type,
              template_version, status, retry_count
         FROM notification_logs WHERE event_id = ?`,
      [eventId],
    );
    return rows;
  };

  beforeAll(() => {
    pool = createPool({
      host: db.host,
      port: db.port,
      database: db.name,
      user: db.username,
      password: db.password,
      timezone: 'Z',
      connectionLimit: 10,
    });
    const email = new NodemailerEmailAdapter(
      new ConfigService<AppConfig, true>(config),
      createTransport as never,
    );
    useCase = new NotifyUserKycResultUseCase(
      new NotificationSender(new MysqlNotificationLogRepository(pool), email, {
        logger: { error: () => undefined },
      }),
    );
  });

  afterAll(async () => {
    try {
      for (const id of mailIds) {
        await fetch(`${MAILHOG}/api/v1/messages/${id}`, { method: 'DELETE' });
      }
      if (eventIds.length > 0) {
        await pool.query(
          'DELETE FROM notification_logs WHERE event_id IN (?)',
          [eventIds],
        );
      }
    } finally {
      await pool.end(); // always, or Jest never exits
    }
  });

  it('delivers the rejection with its reason and records the row', async () => {
    const rejected = command('e1');

    await useCase.execute(rejected);

    expect(await rowsOf(rejected.eventId)).toEqual([
      {
        recipient_email: 'owner-e1@kyc.int.test',
        recipient_user_id: KYC_USER_ID,
        event_type: 'notification.user_kyc_result_requested',
        notification_type: 'USER_KYC_RESULT',
        template_version: 'user-kyc-result/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining('owner-e1@kyc.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] Your organisation was not verified',
    );
    // An ASCII name and reason: every line within 76 characters, sent as 7bit.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      '  The business licence is not legible.',
    );
    expect(mails[0].Content.Body).toContain(
      '2026-11-11 09:30:00 Vietnam time (2026-11-11T02:30:00.123Z UTC)',
    );
  });

  it('delivers a verification, whose command carries no reason', async () => {
    const verified = command('e2', kycVerifiedPayload());

    await useCase.execute(verified);

    const mails = await mailsContaining('owner-e2@kyc.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] Your organisation is verified',
    );
    expect(mails[0].Content.Body).toContain(
      'This organisation has been verified:',
    );
    expect(mails[0].Content.Body).not.toContain('Reason');
  });

  it('a redelivery of the same command sends no second copy', async () => {
    const rejected = command('e3');

    await useCase.execute(rejected);
    await useCase.execute(rejected);

    const mails = await mailsContaining('owner-e3@kyc.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(rejected.eventId)).toHaveLength(1);
  });
});
