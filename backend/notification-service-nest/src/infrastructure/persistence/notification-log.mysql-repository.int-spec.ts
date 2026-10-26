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

    await repo.heartbeat(eventId, 'buyer@it.test');
    expect(await claimAgeSeconds(eventId)).toBeLessThan(5);

    await ageClaim(eventId, 30); // two beats missed would be needed to expire it
    await expect(repo.claim(log(eventId), LEASE_MS)).resolves.toBe(
      'IN_PROGRESS',
    );
  });

  it('a heartbeat touches only a PENDING row: SENT and FAILED rows keep their updated_at', async () => {
    const sentEvent = newEvent();
    await repo.claim(log(sentEvent), LEASE_MS);
    await repo.markSent(sentEvent, 'buyer@it.test');
    const failedEvent = newEvent();
    await repo.claim(log(failedEvent), LEASE_MS);
    await repo.markFailed(failedEvent, 'buyer@it.test');
    await ageClaim(sentEvent, 100);
    await ageClaim(failedEvent, 100);

    await repo.heartbeat(sentEvent, 'buyer@it.test');
    await repo.heartbeat(failedEvent, 'buyer@it.test');

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
        'buyer@it.test',
        'S',
        'B',
      ),
      new NotificationSender(repo, smtp).send(
        eventId,
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
});
