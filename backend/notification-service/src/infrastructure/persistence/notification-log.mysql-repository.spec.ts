import type { Pool } from 'mysql2/promise';
import { CLAIM_LEASE_MS } from '../../application/notification-sender';
import {
  NotificationChannel,
  NotificationLog,
} from '../../domain/notification-log';
import {
  MysqlNotificationLogRepository,
  PROVIDER_MESSAGE_ID_MAX,
  storableProviderMessageId,
} from './notification-log.mysql-repository';

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

/** No legacy row: the answer of the pre-V2 lookup that starts every claim. */
const noLegacyRow = (): unknown => [[], []];

const KEY = {
  eventId: 'evt-1',
  recipientEmail: 'buyer@example.test',
  notificationType: 'CONTRACT_SIGNED',
};
const KEY_PARAMS = ['evt-1', 'buyer@example.test', 'CONTRACT_SIGNED'];
const PROVIDER_ID = '<cafe1234@agricontract.test>';
const BY_KEY = 'event_id = ? AND recipient_email = ? AND notification_type = ?';

const KIND = {
  eventType: 'contract.signed',
  notificationType: 'CONTRACT_SIGNED',
  templateVersion: 'contract-signed/p1',
};

const newLog = (recipientUserId?: string) =>
  NotificationLog.create(
    'evt-1',
    KIND,
    'buyer@example.test',
    NotificationChannel.EMAIL,
    'Subject',
    'Body',
    recipientUserId,
  );

describe('MysqlNotificationLogRepository.claim', () => {
  it('first looks for the email SENT before V2, on its legacy key', async () => {
    const { pool, query } = scriptedPool(noLegacyRow(), [
      { affectedRows: 1 },
      [],
    ]);

    await new MysqlNotificationLogRepository(pool).claim(
      newLog(),
      CLAIM_LEASE_MS,
    );

    expect(sql(query, 0)).toBe(
      `SELECT 1 FROM notification_logs WHERE ${BY_KEY} AND status = 'SENT'`,
    );
    expect(params(query, 0)).toEqual([
      'evt-1',
      'legacy:buyer@example.test',
      'LEGACY_P1',
    ]);
  });

  it('reports ALREADY_SENT, and writes nothing, when the legacy row is SENT', async () => {
    const { pool, query } = scriptedPool([[{ 1: 1 }], []]);

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), CLAIM_LEASE_MS),
    ).resolves.toBe('ALREADY_SENT');
    expect(query).toHaveBeenCalledTimes(1);
  });

  it('claims a first delivery by inserting a PENDING row with its kind and DB-clock UTC timestamps', async () => {
    const { pool, query } = scriptedPool(noLegacyRow(), [
      { affectedRows: 1 },
      [],
    ]);
    const log = newLog('user-7');

    await expect(
      new MysqlNotificationLogRepository(pool).claim(log, CLAIM_LEASE_MS),
    ).resolves.toBe('CLAIMED');

    expect(query).toHaveBeenCalledTimes(2);
    expect(sql(query, 1)).toBe(
      'INSERT INTO notification_logs (notification_id, event_id, event_type, notification_type, user_id, recipient_user_id, ' +
        'recipient_email, channel, subject, body, template_version, status, retry_count, created_at, updated_at) ' +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
    );
    // user_id still receives the recipient email, as before V2; the platform id
    // of the recipient goes to recipient_user_id, and the kind names the template.
    expect(params(query, 1)).toEqual([
      log.notificationId,
      'evt-1',
      'contract.signed',
      'CONTRACT_SIGNED',
      'buyer@example.test',
      'user-7',
      'buyer@example.test',
      'EMAIL',
      'Subject',
      'Body',
      'contract-signed/p1',
    ]);
  });

  it('writes recipient_user_id as NULL when the sender has only the address', async () => {
    const { pool, query } = scriptedPool(noLegacyRow(), [
      { affectedRows: 1 },
      [],
    ]);

    await new MysqlNotificationLogRepository(pool).claim(
      newLog(),
      CLAIM_LEASE_MS,
    );

    expect(params(query, 1)[5]).toBeNull();
  });

  it('on an existing row, retakes it by its key only if FAILED or PENDING older than the lease', async () => {
    const { pool, query } = scriptedPool(noLegacyRow(), duplicate(), [
      { affectedRows: 1 },
      [],
    ]);

    await expect(
      new MysqlNotificationLogRepository(pool).claim(
        newLog('user-7'),
        CLAIM_LEASE_MS,
      ),
    ).resolves.toBe('CLAIMED');

    expect(sql(query, 2)).toBe(
      "UPDATE notification_logs SET status = 'PENDING', updated_at = UTC_TIMESTAMP(6), " +
        'provider_message_id = NULL, ' +
        'recipient_user_id = ?, subject = ?, body = ?, template_version = ? ' +
        `WHERE ${BY_KEY} AND (status = 'FAILED' OR (status = 'PENDING' ` +
        'AND updated_at < UTC_TIMESTAMP(6) - INTERVAL ? MICROSECOND))',
    );
    expect(params(query, 2)).toEqual([
      'user-7',
      'Subject',
      'Body',
      'contract-signed/p1',
      ...KEY_PARAMS,
      45_000_000,
    ]);
  });

  it('the retake overwrites what the failed attempt rendered, so the row describes the email sent now', async () => {
    const { pool, query } = scriptedPool(noLegacyRow(), duplicate(), [
      { affectedRows: 1 },
      [],
    ]);
    const log = NotificationLog.create(
      'evt-1',
      { ...KIND, templateVersion: 'contract-signed/p2' },
      'buyer@example.test',
      NotificationChannel.EMAIL,
      'New subject',
      'New body',
    );

    await expect(
      new MysqlNotificationLogRepository(pool).claim(log, CLAIM_LEASE_MS),
    ).resolves.toBe('CLAIMED');

    // Set, not read: a template bumped between the failure and the redelivery
    // must not leave the row claiming the old version was sent.
    expect(params(query, 2).slice(0, 4)).toEqual([
      null,
      'New subject',
      'New body',
      'contract-signed/p2',
    ]);
  });

  it('reports ALREADY_SENT when the row could not be retaken and is SENT', async () => {
    const { pool, query } = scriptedPool(
      noLegacyRow(),
      duplicate(),
      [{ affectedRows: 0 }, []],
      [[{ status: 'SENT' }], []],
    );

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), CLAIM_LEASE_MS),
    ).resolves.toBe('ALREADY_SENT');
    expect(sql(query, 3)).toBe(
      `SELECT status FROM notification_logs WHERE ${BY_KEY}`,
    );
    expect(params(query, 3)).toEqual(KEY_PARAMS);
  });

  it('reports IN_PROGRESS when another delivery holds a fresh PENDING claim', async () => {
    const { pool } = scriptedPool(
      noLegacyRow(),
      duplicate(),
      [{ affectedRows: 0 }, []],
      [[{ status: 'PENDING' }], []],
    );

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), CLAIM_LEASE_MS),
    ).resolves.toBe('IN_PROGRESS');
  });

  it('rethrows insert errors other than a duplicate key', async () => {
    const { pool } = scriptedPool(noLegacyRow(), new Error('connection lost'));

    await expect(
      new MysqlNotificationLogRepository(pool).claim(newLog(), CLAIM_LEASE_MS),
    ).rejects.toThrow('connection lost');
  });
});

describe('MysqlNotificationLogRepository.heartbeat', () => {
  it('moves updated_at of the PENDING row to the DB clock, and touches nothing else', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).heartbeat(KEY);

    expect(sql(query, 0)).toBe(
      `UPDATE notification_logs SET updated_at = UTC_TIMESTAMP(6) WHERE ${BY_KEY} AND status = 'PENDING'`,
    );
    expect(params(query, 0)).toEqual(KEY_PARAMS);
  });
});

describe('MysqlNotificationLogRepository.markSent / markFailed', () => {
  it('markSent sets SENT, the provider message id and sent_at by the key', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).markSent(KEY, PROVIDER_ID);

    expect(sql(query, 0)).toBe(
      "UPDATE notification_logs SET status = 'SENT', provider_message_id = ?, " +
        `sent_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6) WHERE ${BY_KEY}`,
    );
    expect(params(query, 0)).toEqual([PROVIDER_ID, ...KEY_PARAMS]);
  });

  it('markSent stores no provider id when the provider named none', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).markSent(KEY, null);

    expect(params(query, 0)).toEqual([null, ...KEY_PARAMS]);
  });

  /*
   * The email is already sent when markSent runs: an UPDATE the server refuses
   * would leave the row PENDING and send that email again after the lease. An
   * id wider than the column is therefore dropped, not truncated and not
   * passed on to fail.
   */
  it('markSent drops a provider id wider than the column instead of failing', async () => {
    const { pool, query } = scriptedPool();
    const tooLong = `<${'a'.repeat(PROVIDER_MESSAGE_ID_MAX)}@provider.test>`;

    await new MysqlNotificationLogRepository(pool).markSent(KEY, tooLong);

    expect(params(query, 0)).toEqual([null, ...KEY_PARAMS]);
    expect(storableProviderMessageId('a'.repeat(PROVIDER_MESSAGE_ID_MAX))).toBe(
      'a'.repeat(PROVIDER_MESSAGE_ID_MAX),
    );
  });

  it('markFailed sets FAILED, increments retry_count in SQL and stores the failure code', async () => {
    const { pool, query } = scriptedPool();

    await new MysqlNotificationLogRepository(pool).markFailed(
      KEY,
      'EENVELOPE/550',
    );

    expect(sql(query, 0)).toBe(
      "UPDATE notification_logs SET status = 'FAILED', retry_count = retry_count + 1, failure_reason = ?, " +
        `updated_at = UTC_TIMESTAMP(6) WHERE ${BY_KEY}`,
    );
    expect(params(query, 0)).toEqual(['EENVELOPE/550', ...KEY_PARAMS]);
  });
});
