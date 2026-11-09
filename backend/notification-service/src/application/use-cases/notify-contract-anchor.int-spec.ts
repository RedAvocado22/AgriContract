/**
 * Integration test of the signed-contract email (#278,
 * `notification.contract_anchor_requested`) against a real MySQL (V2
 * notification_logs) and a real MailHog, run with
 *   npm run test:int
 * DB_*, MAIL_HOST and MAIL_PORT apply with the usual defaults (localhost:3311,
 * localhost:1025); MAILHOG_API_URL defaults to http://localhost:8025.
 *
 * The command goes through the real parser, so the path from published bytes to
 * a stored row and a delivered email is the production one, minus RabbitMQ
 * (the consumer's own wiring is covered by notification-event.consumer.spec).
 */
import { ConfigService } from '@nestjs/config';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import {
  anchorBody,
  anchorPayload,
  TERMS_SNAPSHOT,
  TERMS_SNAPSHOT_HASH,
} from '../../../test/support/commands';
import { RFC8785_HASH, RFC8785_INPUT, sha256 } from '../../../test/support/jcs';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../../infrastructure/email/nodemailer-email.adapter';
import { parseContractAnchorRequested } from '../../infrastructure/messaging/notification-command.parsers';
import { MysqlNotificationLogRepository } from '../../infrastructure/persistence/notification-log.mysql-repository';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import { NotifyContractAnchorUseCase } from './notify-contract-anchor.use-case';
import { createTransport } from 'nodemailer';

const config = validate(process.env);
const db = config.db;
const MAILHOG = process.env.MAILHOG_API_URL ?? 'http://localhost:8025';
const KIND = COMMAND_KINDS.contractAnchor;

interface MailHogMessage {
  ID: string;
  Content: {
    Headers: {
      To: string[];
      Subject: string[];
      'Content-Transfer-Encoding'?: string[];
    };
    Body: string;
  };
}

/**
 * The body as the recipient's mail client shows it: MailHog keeps the
 * transfer encoding, and nodemailer picks quoted-printable or base64 for a
 * line as long as the canonical terms.
 */
function decodedBody(mail: MailHogMessage): string {
  const encoding = (
    mail.Content.Headers['Content-Transfer-Encoding']?.[0] ?? '7bit'
  ).toLowerCase();
  const raw = mail.Content.Body;
  let bytes: Buffer;
  if (encoding === 'quoted-printable') {
    const unwrapped = raw.replace(/=\r?\n/g, '');
    bytes = Buffer.from(
      unwrapped.replace(/=([0-9A-F]{2})/gi, (_, hex: string) =>
        String.fromCharCode(parseInt(hex, 16)),
      ),
      'latin1',
    );
  } else if (encoding === 'base64') {
    bytes = Buffer.from(raw, 'base64');
  } else {
    bytes = Buffer.from(raw, 'utf8');
  }
  return bytes.toString('utf8').replace(/\r\n/g, '\n');
}

async function mailsContaining(text: string): Promise<MailHogMessage[]> {
  const res = await fetch(
    `${MAILHOG}/api/v2/search?kind=containing&query=${encodeURIComponent(text)}`,
  );
  return ((await res.json()) as { items: MailHogMessage[] }).items;
}

describe('contract anchor email on real MySQL and MailHog', () => {
  let pool: Pool;
  let useCase: NotifyContractAnchorUseCase;
  const eventIds: string[] = [];
  const mailIds: string[] = [];

  /** A command whose eventId and recipients are unique to one test. */
  const command = (tag: string) => {
    const eventId = `${'0'.repeat(8)}-0000-4000-8000-${tag.padStart(12, '0')}`;
    eventIds.push(eventId);
    return parseContractAnchorRequested(
      anchorBody({
        eventId,
        payload: anchorPayload({
          recipients: [
            {
              userId: 'd2a1f3b4-6c5d-4e7f-8a9b-0c1d2e3f4a5b',
              email: `buyer-${tag}@anchor.int.test`,
              role: 'BUYER',
            },
            {
              userId: null,
              email: `seller-${tag}@anchor.int.test`,
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
              template_version, status, retry_count, user_id,
              sent_at IS NOT NULL AS has_sent_at
         FROM notification_logs
        WHERE event_id = ?
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
    useCase = new NotifyContractAnchorUseCase(
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

  it('delivers one email per recipient and records a row for each', async () => {
    const anchor = command('a1');

    await useCase.execute(anchor);

    const rows = await rowsOf(anchor.eventId);
    expect(rows).toEqual([
      {
        recipient_email: 'buyer-a1@anchor.int.test',
        recipient_user_id: 'd2a1f3b4-6c5d-4e7f-8a9b-0c1d2e3f4a5b',
        event_type: KIND.eventType,
        notification_type: KIND.notificationType,
        template_version: KIND.templateVersion,
        status: 'SENT',
        retry_count: 0,
        // Phase 1's column still holds the address (design §6, V3 decides its fate).
        user_id: 'buyer-a1@anchor.int.test',
        has_sent_at: 1,
      },
      {
        recipient_email: 'seller-a1@anchor.int.test',
        recipient_user_id: null,
        event_type: KIND.eventType,
        notification_type: KIND.notificationType,
        template_version: KIND.templateVersion,
        status: 'SENT',
        retry_count: 0,
        user_id: 'seller-a1@anchor.int.test',
        has_sent_at: 1,
      },
    ]);

    const mails = await mailsContaining('buyer-a1@anchor.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(mails[0].Content.Headers.Subject[0]).toBe(
      `[AgriContract] Signed contract ${anchor.contractId}`,
    );
    // The hash and the terms it covers both reach the recipient.
    expect(mails[0].Content.Body).toContain(anchor.signedContentHash);
    expect(mails[0].Content.Body).toContain('110400000.00');
  });

  /*
   * The check the email invites, on the delivered mail: decode the transfer
   * encoding, take the canonical line, hash it. The RFC 8785 sample carries a
   * non-ASCII character and escapes, the cases an encoding would break.
   */
  it.each([
    ['the terms fixture', 'a4', TERMS_SNAPSHOT, TERMS_SNAPSHOT_HASH],
    ['the RFC 8785 sample', 'a5', JSON.parse(RFC8785_INPUT), RFC8785_HASH],
  ])(
    'the delivered canonical line of %s hashes to signedContentHash',
    async (_name, tag, snapshot, hash) => {
      const anchor = command(tag);
      anchor.contractTermsSnapshot = snapshot as Record<string, unknown>;
      anchor.signedContentHash = hash;

      await useCase.execute(anchor);

      const mails = await mailsContaining(`buyer-${tag}@anchor.int.test`);
      mailIds.push(...mails.map((m) => m.ID));
      expect(mails).toHaveLength(1);
      const lines = decodedBody(mails[0]).split('\n');
      const canonical =
        lines[
          lines.indexOf('Canonical form (this is what the hash covers):') + 1
        ];
      expect(sha256(canonical)).toBe(hash);
    },
  );

  it('a redelivery of the same command sends no second copy', async () => {
    const anchor = command('a2');

    await useCase.execute(anchor);
    await useCase.execute(anchor);

    const mails = await mailsContaining('buyer-a2@anchor.int.test');
    mailIds.push(...mails.map((m) => m.ID));
    expect(mails).toHaveLength(1);
    expect(await rowsOf(anchor.eventId)).toHaveLength(2);
  });

  /*
   * The dedup key is (event, recipient, type), so the same recipient of the
   * same contract gets this email and any other type the contract sends.
   */
  it('the unique key refuses a second row of the same event, recipient and type', async () => {
    const anchor = command('a3');
    await useCase.execute(anchor);

    const mails = await mailsContaining('buyer-a3@anchor.int.test');
    mailIds.push(...mails.map((m) => m.ID));

    await expect(
      pool.query(
        `INSERT INTO notification_logs
           (notification_id, event_id, event_type, notification_type, user_id,
            recipient_email, channel, subject, body, template_version, status,
            retry_count, created_at, updated_at)
         VALUES (UUID(), ?, ?, ?, ?, ?, 'EMAIL', 'S', 'B', ?, 'PENDING', 0,
                 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
        [
          anchor.eventId,
          KIND.eventType,
          KIND.notificationType,
          'buyer-a3@anchor.int.test',
          'buyer-a3@anchor.int.test',
          KIND.templateVersion,
        ],
      ),
    ).rejects.toMatchObject({ code: 'ER_DUP_ENTRY' });
  });
});
