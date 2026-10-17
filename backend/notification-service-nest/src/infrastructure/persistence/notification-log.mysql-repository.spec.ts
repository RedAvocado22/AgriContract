import type { Pool } from 'mysql2/promise';
import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from '../../domain/notification-log';
import { MysqlNotificationLogRepository } from './notification-log.mysql-repository';

function fakePool(query: jest.Mock) {
  return { query } as unknown as Pool;
}

const newLog = () =>
  NotificationLog.create(
    'evt-1',
    'buyer@example.test',
    NotificationChannel.EMAIL,
    'Subject',
    'Body',
  );

describe('MysqlNotificationLogRepository', () => {
  it('finds by event id and recipient email (user_id column)', async () => {
    const query = jest.fn().mockResolvedValue([
      [
        {
          notification_id: 'n-1',
          event_id: 'evt-1',
          user_id: 'buyer@example.test',
          channel: 'EMAIL',
          subject: 'Subject',
          body: 'Body',
          status: 'FAILED',
          retry_count: 2,
        },
      ],
      [],
    ]);
    const repo = new MysqlNotificationLogRepository(fakePool(query));

    const log = await repo.findByEventIdAndRecipient(
      'evt-1',
      'buyer@example.test',
    );

    const [sql, params] = query.mock.calls[0] as [string, unknown[]];
    expect(sql).toMatch(/WHERE event_id = \? AND user_id = \?/);
    expect(params).toEqual(['evt-1', 'buyer@example.test']);
    expect(log?.notificationId).toBe('n-1');
    expect(log?.recipientEmail).toBe('buyer@example.test');
    expect(log?.status).toBe(NotificationStatus.FAILED);
    expect(log?.retryCount).toBe(2);
  });

  it('returns null when there is no log', async () => {
    const repo = new MysqlNotificationLogRepository(
      fakePool(jest.fn().mockResolvedValue([[], []])),
    );
    await expect(repo.findByEventIdAndRecipient('e', 'x@y')).resolves.toBe(
      null,
    );
  });

  it('inserts every column with UTC timestamps written by the app', async () => {
    const query = jest.fn().mockResolvedValue([{ affectedRows: 1 }, []]);
    const repo = new MysqlNotificationLogRepository(fakePool(query));
    const log = newLog();

    await expect(repo.insert(log)).resolves.toBe(true);

    const [sql, params] = query.mock.calls[0] as [string, unknown[]];
    expect(sql).toMatch(
      /\(notification_id, event_id, user_id, channel, subject, body, status, retry_count, created_at, updated_at\)/,
    );
    expect(params.slice(0, 8)).toEqual([
      log.notificationId,
      'evt-1',
      'buyer@example.test',
      'EMAIL',
      'Subject',
      'Body',
      'PENDING',
      0,
    ]);
    expect(params[8]).toBeInstanceOf(Date);
    expect(params[9]).toBe(params[8]);
  });

  it('reports a duplicate (event_id, user_id) as false instead of throwing', async () => {
    const duplicate = Object.assign(new Error('Duplicate entry'), {
      code: 'ER_DUP_ENTRY',
      errno: 1062,
    });
    const repo = new MysqlNotificationLogRepository(
      fakePool(jest.fn().mockRejectedValue(duplicate)),
    );

    await expect(repo.insert(newLog())).resolves.toBe(false);
  });

  it('rethrows other insert errors', async () => {
    const repo = new MysqlNotificationLogRepository(
      fakePool(jest.fn().mockRejectedValue(new Error('connection lost'))),
    );

    await expect(repo.insert(newLog())).rejects.toThrow('connection lost');
  });

  it('updates status, retry count and updated_at by notification id', async () => {
    const query = jest.fn().mockResolvedValue([{ affectedRows: 1 }, []]);
    const repo = new MysqlNotificationLogRepository(fakePool(query));
    const log = newLog();
    log.markFailed();

    await repo.update(log);

    const [sql, params] = query.mock.calls[0] as [string, unknown[]];
    expect(sql).toMatch(
      /SET status = \?, retry_count = \?, updated_at = \?\s+WHERE notification_id = \?/,
    );
    expect(params[0]).toBe('FAILED');
    expect(params[1]).toBe(1);
    expect(params[2]).toBeInstanceOf(Date);
    expect(params[3]).toBe(log.notificationId);
  });
});
