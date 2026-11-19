/**
 * Integration test of the Operator email about a Level 1.5 request waiting
 * for an inspector (`notification.level1_5_assignment_waiting_requested`)
 * against a real MySQL (V2 notification_logs) and a real MailHog, run with
 *   npm run test:int
 * DB_*, MAIL_HOST and MAIL_PORT apply with the usual defaults (localhost:3311,
 * localhost:1025); MAILHOG_API_URL defaults to http://localhost:8025.
 *
 * The command goes through the real parser, so the path from published bytes
 * to a stored row and a delivered email is the production one, minus RabbitMQ.
 * Each test uses its own operator address, so its mails are told apart.
 */
import { ConfigService } from '@nestjs/config';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import { createTransport } from 'nodemailer';
import {
  WAITING_MILESTONE_ID,
  waitingBody,
  waitingPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseLevel15AssignmentWaitingRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyLevel15AssignmentWaitingUseCase } from './notify-level15-assignment-waiting.use-case';

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

describe('Level 1.5 waiting email on real MySQL and MailHog', () => {
  let pool: Pool;
  let sender: NotificationSender;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** The use case mailing an operator address unique to one test. */
  const operator = (tag: string) => ({
    address: `operator-${tag}@waiting.int.test`,
    useCase: new NotifyLevel15AssignmentWaitingUseCase(
      sender,
      `operator-${tag}@waiting.int.test`,
    ),
  });

  /** A command whose eventId is unique to one test. */
  const command = (tag: string, payload = waitingPayload()) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseLevel15AssignmentWaitingRequested(
      waitingBody({ eventId, payload }),
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
    sender = new NotificationSender(
      new MysqlNotificationLogRepository(pool),
      email,
      { logger: { error: () => undefined } },
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

  it('delivers WAITING_STARTED to the operator and records the row', async () => {
    const { address, useCase } = operator('b51');
    const started = command('b51');

    await useCase.execute(started);

    expect(await rowsOf(started.eventId)).toEqual([
      {
        recipient_email: address,
        recipient_user_id: null,
        event_type: 'notification.level1_5_assignment_waiting_requested',
        notification_type: 'LEVEL1_5_WAITING_STARTED',
        template_version: 'level1-5-waiting-started/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining(address);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] A Level 1.5 inspection is waiting for an inspector',
    );
    // Lines within 76 characters: sent as 7bit, the instant reads unbroken.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      `Milestone: ${WAITING_MILESTONE_ID}`,
    );
    expect(mails[0].Content.Body).toContain(
      '2026-11-16 10:00:00 Vietnam time (2026-11-16T03:00:00.000Z UTC)',
    );
  });

  it('delivers STILL_WAITING with its own template', async () => {
    const { address, useCase } = operator('b54');
    const reminder = command('b54', waitingPayload({ stage: 'STILL_WAITING' }));

    await useCase.execute(reminder);

    expect(await rowsOf(reminder.eventId)).toMatchObject([
      {
        notification_type: 'LEVEL1_5_STILL_WAITING',
        template_version: 'level1-5-still-waiting/v1',
        status: 'SENT',
      },
    ]);
    const mails = await mailsContaining(address);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] A Level 1.5 inspection is still waiting for an inspector',
    );
  });

  it('delivers ASSIGNED with its own template', async () => {
    const { address, useCase } = operator('b52');
    const assigned = command('b52', waitingPayload({ stage: 'ASSIGNED' }));

    await useCase.execute(assigned);

    expect(await rowsOf(assigned.eventId)).toMatchObject([
      {
        notification_type: 'LEVEL1_5_ASSIGNED_AFTER_WAIT',
        template_version: 'level1-5-assigned-after-wait/v1',
        status: 'SENT',
      },
    ]);
    const mails = await mailsContaining(address);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] A waiting Level 1.5 inspection now has an inspector',
    );
  });

  // #493 (D57): the dispute ended while the request was still waiting.
  it('delivers CANCELLED with its own template', async () => {
    const { address, useCase } = operator('b55');
    const cancelled = command('b55', waitingPayload({ stage: 'CANCELLED' }));

    await useCase.execute(cancelled);

    expect(await rowsOf(cancelled.eventId)).toMatchObject([
      {
        recipient_email: address,
        notification_type: 'LEVEL1_5_WAITING_CANCELLED',
        template_version: 'level1-5-waiting-cancelled/v1',
        status: 'SENT',
      },
    ]);
    const mails = await mailsContaining(address);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] A waiting Level 1.5 inspection was cancelled',
    );
    expect(mails[0].Content.Body).toContain('No inspector will be assigned.');
  });

  it('a replay of the same eventId sends no second copy', async () => {
    const { address, useCase } = operator('b53');
    const reminder = command('b53', waitingPayload({ stage: 'STILL_WAITING' }));

    await useCase.execute(reminder);
    await useCase.execute(reminder);

    const mails = await mailsContaining(address);
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(reminder.eventId)).toHaveLength(1);
  });
});
