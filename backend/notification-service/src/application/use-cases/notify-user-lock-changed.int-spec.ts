/**
 * Integration test of the lock-changed email
 * (`notification.user_lock_changed_requested`) against a real MySQL (V2
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
  LOCK_USER_ID,
  lockBody,
  lockPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseUserLockChangedRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyUserLockChangedUseCase } from './notify-user-lock-changed.use-case';

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

describe('lock-changed email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyUserLockChangedUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and address are unique to one test. */
  const command = (tag: string, payload = lockPayload()) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseUserLockChangedRequested(
      lockBody({
        eventId,
        payload: { ...payload, recipientEmail: `trader-${tag}@lock.int.test` },
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
    useCase = new NotifyUserLockChangedUseCase(
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

  it('delivers the lock with its end and records the row', async () => {
    const locked = command('9a1');

    await useCase.execute(locked);

    expect(await rowsOf(locked.eventId)).toEqual([
      {
        recipient_email: 'trader-9a1@lock.int.test',
        recipient_user_id: LOCK_USER_ID,
        event_type: 'notification.user_lock_changed_requested',
        notification_type: 'USER_LOCK_CHANGED',
        template_version: 'user-lock-changed/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining('trader-9a1@lock.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] Your account is locked',
    );
    // Lines within 76 characters: sent as 7bit, the instant reads unbroken.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      '2026-12-11 11:00:00 Vietnam time (2026-12-11T04:00:00.000Z UTC)',
    );
  });

  it('delivers an unlock that leaves no lock', async () => {
    const unlocked = command(
      '9a2',
      lockPayload({ action: 'UNLOCKED', effectiveUntil: null }),
    );

    await useCase.execute(unlocked);

    const mails = await mailsContaining('trader-9a2@lock.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] Your account is no longer locked',
    );
  });

  it('a redelivery of the same command sends no second copy', async () => {
    const locked = command('9a3');

    await useCase.execute(locked);
    await useCase.execute(locked);

    const mails = await mailsContaining('trader-9a3@lock.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(locked.eventId)).toHaveLength(1);
  });
});
