import type { Pool } from 'mysql2/promise';
import {
  NotificationChannel,
  NotificationLog,
} from '../../domain/notification-log';
import { MysqlNotificationLogRepository } from './notification-log.mysql-repository';

/** Pool whose query() answers in order with the given results (or errors). */
function scriptedPool(...answers: unknown[]) {
  const query = jest.fn(() => {
    const next = answers.shift();
    return next instanceof Error
      ? Promise.reject(next)
      : Promise.resolve(next ?? [{ affectedRows: 0 }, []]);
  });
  return { pool: { query } as unknown as Pool, query };
}

const sql = (query: jest.Mock, call: number) =>
  (query.mock.calls[call] as [string, unknown[]])[0].replace(/\s+/g, ' ');
const params = (query: jest.Mock, call: number) =>
  (query.mock.calls[call] as [string, unknown[]])[1];

const duplicate = () =>
  Object.assign(new Error('Duplicate entry'), {
    code: 'ER_DUP_ENTRY',
    errno: 1062,
  });

const newLog = () =>
  NotificationLog.create(
    'evt-1',
    'buyer@example.test',
    NotificationChannel.EMAIL,
    'Subject',
    'Body',
  );

describe('MysqlNotificationLogRepository.claim', () => {
  it('claims a first delivery by inserting a PENDING row with DB-clock UTC timestamps', async () => {
    const { pool, query } = scriptedPool([{ affectedRows: 1 }, []]);
    const log = newLog();

    await expect(
      new MysqlNotificationLogRepository(pool).claim(log, 120_000),
    ).resolves.toBe('CLAIMED');

    expect(query).toHaveBeenCalledTimes(1);
    expect(sql(query, 0)).toContain(
      "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
    );
    expect(params(query, 0)).toEqual([
      log.notificationId,
      'evt-1',
      'buyer@example.test',
      'EMAIL',
      'Subject',
      'Body',
    ]);
  });

  it('on an existing row, retakes it only if FAILED or PENDING older than the lease', async () => {
    const { pool, query } = scriptedPool(duplicate(), [
      { affectedRows: 1 },
      [],
    ]);

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), 120_000),
    ).resolves.toBe('CLAIMED');

    expect(sql(query, 1)).toBe(
      "UPDATE notification_logs SET status = 'PENDING', updated_at = UTC_TIMESTAMP(6) " +
        "WHERE event_id = ? AND user_id = ? AND (status = 'FAILED' OR (status = 'PENDING' " +
        'AND updated_at < UTC_TIMESTAMP(6) - INTERVAL ? MICROSECOND))',
    );
    expect(params(query, 1)).toEqual([
      'evt-1',
      'buyer@example.test',
      120_000_000,
    ]);
  });

  it('reports ALREADY_SENT when the row could not be retaken and is SENT', async () => {
    const { pool } = scriptedPool(
      duplicate(),
      [{ affectedRows: 0 }, []],
      [[{ status: 'SENT' }], []],
    );

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), 120_000),
    ).resolves.toBe('ALREADY_SENT');
  });

  it('reports IN_PROGRESS when another delivery holds a fresh PENDING claim', async () => {
    const { pool } = scriptedPool(
      duplicate(),
      [{ affectedRows: 0 }, []],
      [[{ status: 'PENDING' }], []],
    );

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), 120_000),
    ).resolves.toBe('IN_PROGRESS');
  });

  it('rethrows insert errors other than a duplicate key', async () => {
    const { pool } = scriptedPool(new Error('connection lost'));

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), 120_000),
    ).rejects.toThrow('connection lost');
  });
});

describe('MysqlNotificationLogRepository.markSent / markFailed', () => {
  it('markSent sets SENT by (event_id, user_id)', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).markSent(
      'evt-1',
      'buyer@example.test',
    );

    expect(sql(query, 0)).toBe(
      "UPDATE notification_logs SET status = 'SENT', updated_at = UTC_TIMESTAMP(6) WHERE event_id = ? AND user_id = ?",
    );
    expect(params(query, 0)).toEqual(['evt-1', 'buyer@example.test']);
  });

  it('markFailed sets FAILED and increments retry_count in SQL', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).markFailed(
      'evt-1',
      'buyer@example.test',
    );

    expect(sql(query, 0)).toBe(
      "UPDATE notification_logs SET status = 'FAILED', retry_count = retry_count + 1, updated_at = UTC_TIMESTAMP(6) WHERE event_id = ? AND user_id = ?",
    );
  });
});
