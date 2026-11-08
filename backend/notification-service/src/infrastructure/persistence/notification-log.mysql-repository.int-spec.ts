/**
 * Integration test against a real MySQL with the V2 notification_logs table
 * (the compose notification-db). Not part of `npm test`; run it with
 *   npm run test:int
 * DB_* environment variables apply, with the usual defaults (localhost:3311).
 */
import { randomUUID } from 'node:crypto';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import {
  CLAIM_LEASE_MS,
  NotificationSender,
} from '../../application/notification-sender';
import {
  EmailDeliveryError,
  type EmailMessage,
  type EmailPort,
} from '../../application/ports/email.port';
import { validate } from '../../config/env.validation';
import {
  NotificationChannel,
  NotificationLog,
} from '../../domain/notification-log';
import { MysqlNotificationLogRepository } from './notification-log.mysql-repository';

const db = validate(process.env).db;
const LEASE_MS = CLAIM_LEASE_MS;
const KIND = {
  eventType: 'contract.signed',
  notificationType: 'CONTRACT_SIGNED',
  templateVersion: 'contract-signed/p1',
};

describe('MysqlNotificationLogRepository on real MySQL', () => {
  let pool: Pool;
  let repo: MysqlNotificationLogRepository;
  const eventIds: string[] = [];

  const newEvent = () => {
    const id = randomUUID();
    eventIds.push(id);
    return id;
  };
  const log = (eventId: string, to = 'buyer@it.test', kind = KIND) =>
    NotificationLog.create(
      eventId,
      kind,
      to,
      NotificationChannel.EMAIL,
      'S',
      'B',
    );
  const key = (eventId: string, to = 'buyer@it.test', kind = KIND) => ({
    eventId,
    recipientEmail: to,
    notificationType: kind.notificationType,
  });
  const row = async (eventId: string, to = 'buyer@it.test') => {
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT status, retry_count FROM notification_logs WHERE event_id = ? AND recipient_email = ? AND notification_type = ?',
      [eventId, to, KIND.notificationType],
    );
    return rows;
  };
  /** Moves the claim timestamp `seconds` into the past on the DB clock, as if no heartbeat ran since. */
  const ageClaim = async (eventId: string, seconds: number) => {
    await pool.query(
      'UPDATE notification_logs SET updated_at = UTC_TIMESTAMP(6) - INTERVAL ? SECOND WHERE event_id = ?',
      [seconds, eventId],
    );
  };
  const claimAgeSeconds = async (eventId: string) => {
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT TIMESTAMPDIFF(MICROSECOND, updated_at, UTC_TIMESTAMP(6)) / 1e6 AS age FROM notification_logs WHERE event_id = ?',
      [eventId],
    );
    return Number(rows[0]?.age);
  };

  beforeAll(() => {
    pool = createPool({
      host: db.host,
      port: db.port,
      database: db.name,
      user: db.username,
      password: db.password,
      timezone: 'Z',
      connectionLimit: 20,
    });
    repo = new MysqlNotificationLogRepository(pool);
  });

  afterAll(async () => {
    try {
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

  it('10 concurrent first deliveries: exactly one claims, one row', async () => {
    const eventId = newEvent();

    const results = await Promise.all(
      Array.from({ length: 10 }, () => repo.claim(log(eventId), LEASE_MS)),
    );

    expect(results.filter((r) => r === 'CLAIMED')).toHaveLength(1);
    expect(results.filter((r) => r === 'IN_PROGRESS')).toHaveLength(9);
    expect(await row(eventId)).toEqual([{ status: 'PENDING', retry_count: 0 }]);
  });

  it('10 concurrent retries of a FAILED row: exactly one claims', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);
    await repo.markFailed(key(eventId), 'EENVELOPE/550');

    const results = await Promise.all(
      Array.from({ length: 10 }, () => repo.claim(log(eventId), LEASE_MS)),
    );

    expect(results.filter((r) => r === 'CLAIMED')).toHaveLength(1);
    expect(await row(eventId)).toEqual([{ status: 'PENDING', retry_count: 1 }]);
  });

  it('a fresh PENDING row is IN_PROGRESS; once older than the lease it is claimed again', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);

    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe(
      'IN_PROGRESS',
    );

    await ageClaim(eventId, 46);
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe('CLAIMED');
  });

  it('the lease is 45s on the DB clock: a claim 44s old is live, one 46s old is taken over', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);

    await ageClaim(eventId, 44);
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe(
      'IN_PROGRESS',
    );
    await ageClaim(eventId, 46);
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe('CLAIMED');
    expect(await row(eventId)).toEqual([{ status: 'PENDING', retry_count: 0 }]);
    expect(await claimAgeSeconds(eventId)).toBeLessThan(5); // takeover refreshed updated_at
  });

  it('10 concurrent takeovers of a claim older than 45s: exactly one claims', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);
    await ageClaim(eventId, 60);

    const results = await Promise.all(
      Array.from({ length: 10 }, () => repo.claim(log(eventId), LEASE_MS)),
    );

    expect(results.filter((r) => r === 'CLAIMED')).toHaveLength(1);
    expect(results.filter((r) => r === 'IN_PROGRESS')).toHaveLength(9);
  });

  it('a heartbeat keeps a claim alive: a claim about to expire is not taken over once beaten', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);
    await ageClaim(eventId, 44); // the sender is still sending, near the lease

    await repo.heartbeat(key(eventId));
    expect(await claimAgeSeconds(eventId)).toBeLessThan(5);

    await ageClaim(eventId, 30); // two beats missed would be needed to expire it
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe(
      'IN_PROGRESS',
    );
  });

  it('a heartbeat touches only a PENDING row: SENT and FAILED rows keep their updated_at', async () => {
    const sentEvent = newEvent();
    await repo.claim(log(sentEvent), LEASE_MS);
    await repo.markSent(key(sentEvent));
    const failedEvent = newEvent();
    await repo.claim(log(failedEvent), LEASE_MS);
    await repo.markFailed(key(failedEvent), 'EENVELOPE/550');
    await ageClaim(sentEvent, 100);
    await ageClaim(failedEvent, 100);

    await repo.heartbeat(key(sentEvent));
    await repo.heartbeat(key(failedEvent));

    expect(await claimAgeSeconds(sentEvent)).toBeGreaterThanOrEqual(100);
    expect(await claimAgeSeconds(failedEvent)).toBeGreaterThanOrEqual(100);
    expect(await row(sentEvent)).toEqual([{ status: 'SENT', retry_count: 0 }]);
    expect(await row(failedEvent)).toEqual([
      { status: 'FAILED', retry_count: 1 },
    ]);
  });

  it('SENT is final; markFailed counts failures', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);
    await repo.markFailed(key(eventId), 'EENVELOPE/550');
    await repo.claim(log(eventId), LEASE_MS);
    await repo.markFailed(key(eventId), 'EENVELOPE/550');
    expect(await row(eventId)).toEqual([{ status: 'FAILED', retry_count: 2 }]);

    await repo.claim(log(eventId), LEASE_MS);
    await repo.markSent(key(eventId));

    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe(
      'ALREADY_SENT',
    );
    expect(await row(eventId)).toEqual([{ status: 'SENT', retry_count: 2 }]);
  });

  it('probe from review #172: two deliveries race over a slow SMTP send -> 1 email, 1 SENT row', async () => {
    const eventId = newEvent();
    const sent: EmailMessage[] = [];
    const slowSmtp: EmailPort = {
      send: async (message) => {
        await new Promise((r) => setTimeout(r, 300));
        sent.push(message);
      },
    };
    const deliveryA = new NotificationSender(repo, slowSmtp);
    const deliveryB = new NotificationSender(repo, slowSmtp);

    const outcomes = await Promise.allSettled([
      deliveryA.send(eventId, KIND, 'buyer@it.test', 'S', 'B'),
      deliveryB.send(eventId, KIND, 'buyer@it.test', 'S', 'B'),
    ]);

    // The loser waits for the claim (1s polls), finds SENT and sends nothing.
    expect(sent).toHaveLength(1);
    expect(outcomes.map((o) => o.status)).toEqual(['fulfilled', 'fulfilled']);
    expect(await row(eventId)).toEqual([{ status: 'SENT', retry_count: 0 }]);
  });

  it('claim left by a delivery that died mid-send (42s old): two redeliveries wait, exactly one takes over and sends', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS); // the dead delivery's claim, never beaten again
    await ageClaim(eventId, 42);
    const sent: EmailMessage[] = [];
    const smtp: EmailPort = {
      send: (message) => {
        sent.push(message);
        return Promise.resolve();
      },
    };
    const started = Date.now();

    const outcomes = await Promise.allSettled([
      new NotificationSender(repo, smtp).send(
        eventId,
        KIND,
        'buyer@it.test',
        'S',
        'B',
      ),
      new NotificationSender(repo, smtp).send(
        eventId,
        KIND,
        'buyer@it.test',
        'S',
        'B',
      ),
    ]);

    expect(outcomes.map((o) => o.status)).toEqual(['fulfilled', 'fulfilled']);
    expect(sent).toHaveLength(1);
    expect(await row(eventId)).toEqual([{ status: 'SENT', retry_count: 0 }]);
    // Taken over once 45s old, i.e. about 3s of 1s polls, well inside the 50s wait.
    expect(Date.now() - started).toBeGreaterThanOrEqual(2_000);
    expect(Date.now() - started).toBeLessThan(10_000);
  }, 20_000);

  describe('dedup by (event_id, recipient_email, notification_type) (#115, schema V2)', () => {
    const OTHER_KIND = {
      eventType: 'contract.signed',
      notificationType: 'OTHER_MAIL',
      templateVersion: 'other-mail/v1',
    };
    /** SMTP that records mail; addresses in `failFor` are refused like a 550. */
    const recordingSmtp = () => {
      const sent: EmailMessage[] = [];
      const failFor = new Set<string>();
      const smtp: EmailPort = {
        send: (message) => {
          if (failFor.has(message.to)) {
            return Promise.reject(new EmailDeliveryError('EENVELOPE/550'));
          }
          sent.push(message);
          return Promise.resolve();
        },
      };
      return { smtp, sent, failFor };
    };
    const rowsOf = async (eventId: string) => {
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT recipient_email, user_id, recipient_user_id, event_type, notification_type,
                template_version, status, retry_count,
                failure_reason, sent_at IS NOT NULL AS has_sent_at
           FROM notification_logs WHERE event_id = ? ORDER BY id`,
        [eventId],
      );
      return rows.map((r): Record<string, unknown> => ({
        ...r,
        has_sent_at: Number(r.has_sent_at),
      }));
    };
    /** A Phase 1 row as migration V2 leaves it: legacy recipient, LEGACY_P1 defaults. */
    const insertLegacyRow = async (
      eventId: string,
      email: string,
      status: string,
    ) => {
      await pool.query(
        `INSERT INTO notification_logs
           (notification_id, event_id, user_id, recipient_email, channel, subject, body,
            status, retry_count, created_at, updated_at)
         VALUES (UUID(), ?, ?, CONCAT('legacy:', ?), 'EMAIL', 's', 'b', ?, 0,
                 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
        [eventId, email, email, status],
      );
    };
    const newPool = () =>
      createPool({
        host: db.host,
        port: db.port,
        database: db.name,
        user: db.username,
        password: db.password,
        timezone: 'Z',
        connectionLimit: 2,
      });

    it('two recipients of one event: two emails, two SENT rows with their kind', async () => {
      const eventId = newEvent();
      const { smtp, sent } = recordingSmtp();
      const sender = new NotificationSender(repo, smtp);

      await sender.send(eventId, KIND, 'buyer@it.test', 'S', 'B');
      await sender.send(eventId, KIND, 'seller@it.test', 'S', 'B');

      expect(sent.map((m) => m.to)).toEqual([
        'buyer@it.test',
        'seller@it.test',
      ]);
      const sentRow = {
        event_type: 'contract.signed',
        notification_type: 'CONTRACT_SIGNED',
        template_version: 'contract-signed/p1',
        recipient_user_id: null, // a Phase 1 payload carries addresses only
        status: 'SENT',
        retry_count: 0,
        failure_reason: null,
        has_sent_at: 1,
      };
      expect(await rowsOf(eventId)).toEqual([
        {
          ...sentRow,
          recipient_email: 'buyer@it.test',
          user_id: 'buyer@it.test',
        },
        {
          ...sentRow,
          recipient_email: 'seller@it.test',
          user_id: 'seller@it.test',
        },
      ]);
    });

    it('one event and recipient, two notification types: both claimed, two rows (the old key would refuse)', async () => {
      const eventId = newEvent();
      await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe('CLAIMED');
      await expect(
        repo.claim(log(eventId, 'buyer@it.test', OTHER_KIND), LEASE_MS),
      ).resolves.toBe('CLAIMED');

      expect(
        (await rowsOf(eventId)).map((r) => ({
          notification_type: r.notification_type as string,
          template_version: r.template_version as string,
        })),
      ).toEqual([
        {
          notification_type: 'CONTRACT_SIGNED',
          template_version: 'contract-signed/p1',
        },
        { notification_type: 'OTHER_MAIL', template_version: 'other-mail/v1' },
      ]);
    });

    it('template_version records the template of each row; p1-unknown stays the pre-V2 default', async () => {
      const eventId = newEvent();
      await insertLegacyRow(eventId, 'buyer@it.test', 'FAILED');
      const { smtp } = recordingSmtp();

      await new NotificationSender(repo, smtp).send(
        eventId,
        KIND,
        'buyer@it.test',
        'S',
        'B',
      );

      // The row written before V2 kept the column default; the new one names its template.
      expect(
        (await rowsOf(eventId)).map((r) => r.template_version as string),
      ).toEqual(['p1-unknown', 'contract-signed/p1']);
    });

    it('the unique key refuses a second row with the same event, recipient and type', async () => {
      const eventId = newEvent();
      await repo.claim(log(eventId), LEASE_MS);

      await expect(
        pool.query(
          `INSERT INTO notification_logs
             (notification_id, event_id, notification_type, user_id, recipient_email,
              channel, subject, body, status, retry_count)
           VALUES (UUID(), ?, 'CONTRACT_SIGNED', 'x', 'buyer@it.test', 'EMAIL', 's', 'b', 'PENDING', 0)`,
          [eventId],
        ),
      ).rejects.toMatchObject({ code: 'ER_DUP_ENTRY' });
    });

    it('retries never add rows: two failures then a success is one row, failure code kept, SENT once', async () => {
      const eventId = newEvent();
      const { smtp, sent, failFor } = recordingSmtp();
      const sender = new NotificationSender(repo, smtp);
      failFor.add('buyer@it.test');

      for (let i = 0; i < 2; i++) {
        await expect(
          sender.send(eventId, KIND, 'buyer@it.test', 'S', 'B'),
        ).rejects.toThrow('Event ' + eventId);
      }
      expect(await rowsOf(eventId)).toMatchObject([
        {
          status: 'FAILED',
          retry_count: 2,
          failure_reason: 'EENVELOPE/550',
          has_sent_at: 0,
        },
      ]);

      failFor.clear();
      await sender.send(eventId, KIND, 'buyer@it.test', 'S', 'B');
      await sender.send(eventId, KIND, 'buyer@it.test', 'S', 'B'); // redelivery after SENT

      expect(sent).toHaveLength(1);
      expect(await rowsOf(eventId)).toMatchObject([
        { status: 'SENT', retry_count: 2, has_sent_at: 1 },
      ]);
    });

    it('status survives a restart: a new pool and repository send nothing for SENT and retry FAILED in place', async () => {
      const sentEvent = newEvent();
      const failedEvent = newEvent();
      const before = newPool();
      try {
        const { smtp, failFor } = recordingSmtp();
        const sender = new NotificationSender(
          new MysqlNotificationLogRepository(before),
          smtp,
        );
        await sender.send(sentEvent, KIND, 'buyer@it.test', 'S', 'B');
        failFor.add('buyer@it.test');
        await expect(
          sender.send(failedEvent, KIND, 'buyer@it.test', 'S', 'B'),
        ).rejects.toThrow();
      } finally {
        await before.end(); // the service stops: no connection or state is left
      }

      const after = newPool();
      try {
        const { smtp, sent } = recordingSmtp();
        const sender = new NotificationSender(
          new MysqlNotificationLogRepository(after),
          smtp,
        );
        await sender.send(sentEvent, KIND, 'buyer@it.test', 'S', 'B');
        await sender.send(failedEvent, KIND, 'buyer@it.test', 'S', 'B');

        expect(sent.map((m) => m.subject)).toEqual(['S']); // only the FAILED one
      } finally {
        await after.end();
      }
      expect(await rowsOf(sentEvent)).toMatchObject([
        { status: 'SENT', retry_count: 0 },
      ]);
      expect(await rowsOf(failedEvent)).toMatchObject([
        { status: 'SENT', retry_count: 1, failure_reason: 'EENVELOPE/550' },
      ]);
    });

    it('an email SENT before V2 is not sent again on redelivery, and no row is added', async () => {
      const eventId = newEvent();
      await insertLegacyRow(eventId, 'buyer@it.test', 'SENT');
      const { smtp, sent } = recordingSmtp();

      await new NotificationSender(repo, smtp).send(
        eventId,
        KIND,
        'buyer@it.test',
        'S',
        'B',
      );

      expect(sent).toEqual([]);
      expect(await rowsOf(eventId)).toMatchObject([
        {
          recipient_email: 'legacy:buyer@it.test',
          notification_type: 'LEGACY_P1',
          status: 'SENT',
        },
      ]);
    });

    it.each(['FAILED', 'PENDING'])(
      'an email left %s before V2 is sent on redelivery in a new row; the legacy row is untouched',
      async (status) => {
        const eventId = newEvent();
        await insertLegacyRow(eventId, 'buyer@it.test', status);
        const { smtp, sent } = recordingSmtp();

        await new NotificationSender(repo, smtp).send(
          eventId,
          KIND,
          'buyer@it.test',
          'S',
          'B',
        );

        expect(sent).toHaveLength(1);
        expect(await rowsOf(eventId)).toMatchObject([
          {
            recipient_email: 'legacy:buyer@it.test',
            notification_type: 'LEGACY_P1',
            status,
          },
          {
            recipient_email: 'buyer@it.test',
            notification_type: 'CONTRACT_SIGNED',
            status: 'SENT',
          },
        ]);
      },
    );
  });
});
