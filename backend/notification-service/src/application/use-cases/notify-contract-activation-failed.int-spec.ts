/**
 * Integration test of the "deposits could not be locked" email (D42,
 * `notification.contract_activation_failed_requested`) against a real MySQL
 * (V2 notification_logs) and a real MailHog, run with
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
  activationFailedBody,
  activationFailedPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseContractActivationFailedRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyContractActivationFailedUseCase } from './notify-contract-activation-failed.use-case';

const config = validate(process.env);
const db = config.db;
const MAILHOG = process.env.MAILHOG_API_URL ?? 'http://localhost:8025';

const BUYER_ID = 'd2a1f3b4-6c5d-4e7f-8a9b-0c1d2e3f4a5b';
const SELLER_ID = '9e8d7c6b-5a4f-4e3d-2c1b-0a9f8e7d6c5b';

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

describe('contract-activation-failed email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyContractActivationFailedUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and recipients are unique to one test. */
  const command = (tag: string) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseContractActivationFailedRequested(
      activationFailedBody({
        eventId,
        payload: activationFailedPayload({
          recipients: [
            {
              userId: BUYER_ID,
              email: `buyer-${tag}@activation.int.test`,
              role: 'BUYER',
            },
            {
              userId: SELLER_ID,
              email: `seller-${tag}@activation.int.test`,
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
         FROM notification_logs WHERE event_id = ?
        ORDER BY recipient_email`,
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
    useCase = new NotifyContractActivationFailedUseCase(
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

  it('delivers the email to both parties and records a row for each', async () => {
    const failed = command('f1');

    await useCase.execute(failed);

    const row = (email: string, userId: string) => ({
      recipient_email: email,
      recipient_user_id: userId,
      event_type: 'notification.contract_activation_failed_requested',
      notification_type: 'CONTRACT_ACTIVATION_FAILED',
      template_version: 'contract-activation-failed/v1',
      status: 'SENT',
      retry_count: 0,
    });
    expect(await rowsOf(failed.eventId)).toEqual([
      row('buyer-f1@activation.int.test', BUYER_ID),
      row('seller-f1@activation.int.test', SELLER_ID),
    ]);

    for (const party of ['buyer', 'seller']) {
      const mails = await mailsContaining(`${party}-f1@activation.int.test`);
      mailIds.push(...mails.map((m) => m.ID));
      expect(mails).toHaveLength(1);
      expect(mails[0].Content.Headers.Subject[0]).toBe(
        `[AgriContract] Deposits not locked for contract ${failed.contractId}`,
      );
      // Lines within 76 characters: sent as 7bit, the instant reads unbroken.
      expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
        '7bit',
      ]);
      expect(mails[0].Content.Body).toContain(
        "Not locked: the buyer's deposit.",
      );
      expect(mails[0].Content.Body).toContain(
        '2026-11-14 00:00:00 Vietnam time (2026-11-13T17:00:00.000Z UTC)',
      );
    }
  });

  it('a redelivery of the same command sends no second copy', async () => {
    const failed = command('f2');

    await useCase.execute(failed);
    await useCase.execute(failed);

    for (const party of ['buyer', 'seller']) {
      const mails = await mailsContaining(`${party}-f2@activation.int.test`);
      mailIds.push(...mails.map((m) => m.ID));
      expect(mails).toHaveLength(1);
    }
    expect(await rowsOf(failed.eventId)).toHaveLength(2);
  });
});
