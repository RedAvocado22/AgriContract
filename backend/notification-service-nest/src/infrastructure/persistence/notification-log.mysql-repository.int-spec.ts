/**
 * Integration test against a real MySQL with the V1 notification_logs table
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
import type {
  EmailMessage,
  EmailPort,
} from '../../application/ports/email.port';
import { validate } from '../../config/env.validation';
import {
  NotificationChannel,
  NotificationLog,
} from '../../domain/notification-log';
import { MysqlNotificationLogRepository } from './notification-log.mysql-repository';

const db = validate(process.env).db;
const LEASE_MS = CLAIM_LEASE_MS;

describe('MysqlNotificationLogRepository on real MySQL', () => {
  let pool: Pool;
  let repo: MysqlNotificationLogRepository;
  const eventIds: string[] = [];

  const newEvent = () => {
    const id = randomUUID();
    eventIds.push(id);
    return id;
  };
  const log = (eventId: string, to = 'buyer@it.test') =>
    NotificationLog.create(eventId, to, NotificationChannel.EMAIL, 'S', 'B');
  const row = async (eventId: string, to = 'buyer@it.test') => {
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT status, retry_count FROM notification_logs WHERE event_id = ? AND user_id = ?',
      [eventId, to],
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
    await repo.markFailed(eventId, 'buyer@it.test');

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

    await pool.query(
      'UPDATE notification_logs SET updated_at = UTC_TIMESTAMP(6) - INTERVAL 46 SECOND WHERE event_id = ?',
      [eventId],
    );
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe('CLAIMED');
  });

  it('SENT is final; markFailed counts failures', async () => {
    const eventId = newEvent();
    await repo.claim(log(eventId), LEASE_MS);
    await repo.markFailed(eventId, 'buyer@it.test');
    await repo.claim(log(eventId), LEASE_MS);
    await repo.markFailed(eventId, 'buyer@it.test');
    expect(await row(eventId)).toEqual([{ status: 'FAILED', retry_count: 2 }]);

    await repo.claim(log(eventId), LEASE_MS);
    await repo.markSent(eventId, 'buyer@it.test');

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
      deliveryA.send(eventId, 'buyer@it.test', 'S', 'B'),
      deliveryB.send(eventId, 'buyer@it.test', 'S', 'B'),
    ]);

    expect(sent).toHaveLength(1);
    expect(outcomes.filter((o) => o.status === 'fulfilled')).toHaveLength(1);
    expect(outcomes.filter((o) => o.status === 'rejected')).toHaveLength(1); // retried later
    expect(await row(eventId)).toEqual([{ status: 'SENT', retry_count: 0 }]);

    // The loser's retry finds SENT and sends nothing.
    await deliveryB.send(eventId, 'buyer@it.test', 'S', 'B');
    expect(sent).toHaveLength(1);
  });
});
