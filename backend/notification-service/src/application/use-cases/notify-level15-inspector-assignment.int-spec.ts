/**
 * Integration test of the mail to the assigned Level 1.5 inspector
 * (`notification.level1_5_inspector_assignment_requested`, D57) against a
 * real MySQL (V2 notification_logs) and a real MailHog, run with
 *   npm run test:int
 * DB_*, MAIL_HOST and MAIL_PORT apply with the usual defaults (localhost:3311,
 * localhost:1025); MAILHOG_API_URL defaults to http://localhost:8025.
 *
 * The command goes through the real parser, so the path from published bytes
 * to a stored row and a delivered email is the production one, minus RabbitMQ.
 * Each test uses its own inspector address, so its mails are told apart.
 */
import { ConfigService } from '@nestjs/config';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import { createTransport } from 'nodemailer';
import {
  INSPECTOR_COMMISSION_ID,
  INSPECTOR_RECIPIENT,
  inspectorBody,
  inspectorPayload,
} from '../../../test/support/commands';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseLevel15InspectorAssignmentRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { NotificationSender } from '../notification-sender';
import { NotifyLevel15InspectorAssignmentUseCase } from './notify-level15-inspector-assignment.use-case';

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

describe('Level 1.5 inspector email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyLevel15InspectorAssignmentUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** An inspector address unique to one test. */
  const addressOf = (tag: string) => `inspector-${tag}@assignment.int.test`;

  /** A command to that inspector, whose eventId is unique to one test. */
  const command = (tag: string, patch: Record<string, unknown> = {}) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseLevel15InspectorAssignmentRequested(
      inspectorBody({
        eventId,
        payload: inspectorPayload({
          recipients: [{ ...INSPECTOR_RECIPIENT, email: addressOf(tag) }],
          ...patch,
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
    useCase = new NotifyLevel15InspectorAssignmentUseCase(
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

  it('delivers ASSIGNED to the inspector and records the row', async () => {
    const assigned = command('c51');

    await useCase.execute(assigned);

    expect(await rowsOf(assigned.eventId)).toEqual([
      {
        recipient_email: addressOf('c51'),
        recipient_user_id: INSPECTOR_RECIPIENT.userId,
        event_type: 'notification.level1_5_inspector_assignment_requested',
        notification_type: 'LEVEL1_5_INSPECTOR_ASSIGNED',
        template_version: 'level1-5-inspector-assigned/v1',
        status: 'SENT',
        retry_count: 0,
      },
    ]);

    const mails = await mailsContaining(addressOf('c51'));
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] You have been assigned a Level 1.5 inspection',
    );
    // Lines within 76 characters: sent as 7bit, the instants read unbroken.
    expect(mails[0].Content.Headers['Content-Transfer-Encoding']).toEqual([
      '7bit',
    ]);
    expect(mails[0].Content.Body).toContain(
      `Commission: ${INSPECTOR_COMMISSION_ID}`,
    );
    expect(mails[0].Content.Body).toContain(
      '2026-11-21 09:00:00 Vietnam time (2026-11-21T02:00:00.000Z UTC)',
    );
  });

  it('delivers CANCELLED with its own template', async () => {
    const cancelled = command('c52', { stage: 'CANCELLED' });

    await useCase.execute(cancelled);

    expect(await rowsOf(cancelled.eventId)).toMatchObject([
      {
        notification_type: 'LEVEL1_5_INSPECTOR_CANCELLED',
        template_version: 'level1-5-inspector-cancelled/v1',
        status: 'SENT',
      },
    ]);
    const mails = await mailsContaining(addressOf('c52'));
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      '[AgriContract] Your Level 1.5 inspection was cancelled',
    );
    expect(mails[0].Content.Body).toContain('No visit and no report');
  });

  it('a replay of the same eventId sends no second copy', async () => {
    const assigned = command('c53');

    await useCase.execute(assigned);
    await useCase.execute(assigned);

    const mails = await mailsContaining(addressOf('c53'));
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(assigned.eventId)).toHaveLength(1);
  });
});
