/**
 * Integration test of the "offer withdrawn" email (D43,
 * `notification.contract_terminated_requested` with WITHDRAW_OFFER) against a
 * real MySQL (V2 notification_logs) and a real MailHog, run with
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
  terminatedBody,
  terminatedPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseContractTerminatedRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyContractTerminatedUseCase } from './notify-contract-terminated.use-case';

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

describe('offer-withdrawn email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyContractTerminatedUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and recipient are unique to one test; `tag` is hex. */
  const command = (tag: string) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseContractTerminatedRequested(
      terminatedBody({
        eventId,
        payload: terminatedPayload({
          recipients: [
            {
              userId: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
              email: `seller-${tag}@withdraw.int.test`,
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
    useCase = new NotifyContractTerminatedUseCase(
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
    const withdrawn = command('d1');

    await useCase.execute(withdrawn);

    expect(await rowsOf(withdrawn.eventId)).toEqual([
      {
        recipient_email: 'seller-d1@withdraw.int.test',
        recipient_user_id: '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b',
        event_type: 'notification.contract_terminated_requested',
        notification_type: 'OFFER_WITHDRAWN',
        template_version: 'offer-withdrawn/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining('seller-d1@withdraw.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      `[AgriContract] Offer withdrawn for contract ${withdrawn.contractId}`,
    );
    // Lines within 76 characters: sent as 7bit.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      'The buyer has withdrawn the offer for contract',
    );
  });

  it('a redelivery of the same command sends no second copy', async () => {
    const withdrawn = command('d2');

    await useCase.execute(withdrawn);
    await useCase.execute(withdrawn);

    const mails = await mailsContaining('seller-d2@withdraw.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(withdrawn.eventId)).toHaveLength(1);
  });
});
