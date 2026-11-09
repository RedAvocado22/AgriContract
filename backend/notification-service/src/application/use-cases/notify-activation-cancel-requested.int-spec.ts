/**
 * Integration test of the "other party asked to cancel" email (D42,
 * `notification.activation_cancel_requested`) against a real MySQL (V2
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
import { cancelBody, cancelPayload } from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseActivationCancelRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyActivationCancelRequestedUseCase } from './notify-activation-cancel-requested.use-case';

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

describe('activation-cancel-requested email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyActivationCancelRequestedUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and recipient are unique to one test. */
  const command = (tag: string) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseActivationCancelRequested(
      cancelBody({
        eventId,
        payload: cancelPayload({
          recipients: [
            {
              userId: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
              email: `seller-${tag}@cancel.int.test`,
              role: 'SELLER',
            },
          ],
        }),
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
    useCase = new NotifyActivationCancelRequestedUseCase(
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

  it('delivers the email to the other party and records the row', async () => {
    const cancel = command('c1');

    await useCase.execute(cancel);

    expect(await rowsOf(cancel.eventId)).toEqual([
      {
        recipient_email: 'seller-c1@cancel.int.test',
        recipient_user_id: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
        event_type: 'notification.activation_cancel_requested',
        notification_type: 'ACTIVATION_CANCEL_REQUESTED',
        template_version: 'activation-cancel-requested/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining('seller-c1@cancel.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      `[AgriContract] Cancel requested for contract ${cancel.contractId}`,
    );
    // Lines within 76 characters: sent as 7bit, the instant reads unbroken.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      '2026-11-14 10:00:00 Vietnam time (2026-11-14T03:00:00.000Z UTC)',
    );
  });

  it('a redelivery of the same command sends no second copy', async () => {
    const cancel = command('c2');

    await useCase.execute(cancel);
    await useCase.execute(cancel);

    const mails = await mailsContaining('seller-c2@cancel.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(cancel.eventId)).toHaveLength(1);
  });
});
