import path from 'node:path';
import mysql from 'mysql2/promise';
import { GenericContainer, Network, Wait, type StartedNetwork } from 'testcontainers';
import { MySqlContainer, type StartedMySqlContainer } from '@testcontainers/mysql';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { NotificationLogRepository } from '../../src/infrastructure/persistence/notificationLogRepository.js';

// Same versions as production: compose uses mysql:8.0, the Java service ships Flyway 10.10.0.
const MYSQL_IMAGE = 'mysql:8.0';
const FLYWAY_IMAGE = 'flyway/flyway:10.10.0';
const DATABASE = 'notification_db';
const ROOT_PASSWORD = 'test';
const MIGRATIONS = path.resolve(import.meta.dirname, '../../db/migration');

// user_id is VARCHAR(255); this one does not fit behind the 'legacy:' prefix.
const LONG_EMAIL = `${'a'.repeat(240)}@test.local`;

// Phase 1 shape: several rows per event_id, told apart only by user_id (Java stores the email).
const PHASE1_ROWS = [
  ['n-1', 'evt-signed', 'buyer@test.local', 'Contract has been signed', 'SENT', 0],
  ['n-2', 'evt-signed', 'seller@test.local', 'Contract has been signed', 'SENT', 0],
  ['n-3', 'evt-disputed', 'admin@agricontract.com', 'New dispute', 'SENT', 0],
  ['n-4', 'evt-disputed', 'seller@test.local', 'New dispute', 'FAILED', 3],
  ['n-5', 'evt-disputed', 'buyer@test.local', 'New dispute', 'PENDING', 1],
  ['n-6', 'evt-locked', 'seller@test.local', 'Escrow locked', 'SENT', 0],
  ['n-7', 'evt-released', LONG_EMAIL, 'Payment has been released', 'SENT', 0],
  ['n-8', 'evt-released', 'buyer@test.local', 'Payment has been released', 'SENT', 0],
] as const;

function legacyRecipient(userId: string): string {
  return userId.length <= 248 ? `legacy:${userId}` : userId;
}

const PHASE1_COLUMNS =
  'id, notification_id, event_id, user_id, channel, subject, body, status, retry_count, created_at, updated_at';

async function runFlyway(
  network: StartedNetwork,
  database: string,
  ...extraArgs: string[]
): Promise<void> {
  await new GenericContainer(FLYWAY_IMAGE)
    .withNetwork(network)
    .withCopyDirectoriesToContainer([{ source: MIGRATIONS, target: '/flyway/sql' }])
    .withCommand([
      `-url=jdbc:mysql://mysql:3306/${database}?allowPublicKeyRetrieval=true&useSSL=false`,
      '-user=root',
      `-password=${ROOT_PASSWORD}`,
      '-locations=filesystem:/flyway/sql',
      '-connectRetries=10',
      ...extraArgs,
      'migrate',
    ])
    .withLogConsumer((stream) => stream.pipe(process.stdout))
    // Fails start() if Flyway exits non-zero.
    .withWaitStrategy(Wait.forOneShotStartup())
    .start();
}

async function countRows(pool: mysql.Pool, database = DATABASE): Promise<number> {
  const [rows] = await pool.query<mysql.RowDataPacket[]>(
    `SELECT COUNT(*) AS n FROM ${database}.notification_logs`,
  );
  return Number(rows[0]?.n);
}

describe('notification_logs migrations V1 -> V3', () => {
  let network: StartedNetwork;
  let db: StartedMySqlContainer;
  let pool: mysql.Pool;

  beforeAll(async () => {
    network = await new Network().start();
    db = await new MySqlContainer(MYSQL_IMAGE)
      .withDatabase(DATABASE)
      .withRootPassword(ROOT_PASSWORD)
      .withNetwork(network)
      .withNetworkAliases('mysql')
      .start();
    pool = mysql.createPool({
      host: db.getHost(),
      port: db.getPort(),
      database: DATABASE,
      user: 'root',
      password: ROOT_PASSWORD,
      connectionLimit: 4,
    });
  });

  afterAll(async () => {
    await pool.end();
    await db.stop();
    await network.stop();
  });

  it('migrates a Phase 1 database without losing rows', async () => {
    await runFlyway(network, DATABASE, '-target=1');

    for (const [notificationId, eventId, userId, subject, status, retryCount] of PHASE1_ROWS) {
      await pool.execute(
        `INSERT INTO notification_logs
           (notification_id, event_id, user_id, channel, subject, body, status, retry_count,
            created_at, updated_at)
         VALUES (?, ?, ?, 'EMAIL', ?, ?, ?, ?, '2026-05-01 08:00:00.000000',
                 '2026-05-01 08:00:01.000000')`,
        [notificationId, eventId, userId, subject, `body for ${userId}`, status, retryCount],
      );
    }
    const countBefore = await countRows(pool);
    const [before] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT ${PHASE1_COLUMNS} FROM notification_logs ORDER BY id`,
    );

    await runFlyway(network, DATABASE);

    const [history] = await pool.query<mysql.RowDataPacket[]>(
      'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank',
    );
    expect(history).toEqual([
      { version: '1', success: 1 },
      { version: '2', success: 1 },
      { version: '3', success: 1 },
    ]);

    const countAfter = await countRows(pool);
    expect(countBefore).toBe(PHASE1_ROWS.length);
    expect(countAfter).toBe(countBefore);

    const [after] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT ${PHASE1_COLUMNS} FROM notification_logs ORDER BY id`,
    );
    expect(after).toEqual(before);

    const [backfilled] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT user_id, recipient_email, event_type, notification_type, template_version,
              provider_message_id, failure_reason, sent_at
         FROM notification_logs ORDER BY id`,
    );
    expect(backfilled).toEqual(
      PHASE1_ROWS.map(([, , userId]) => ({
        user_id: userId,
        recipient_email: legacyRecipient(userId),
        event_type: 'LEGACY_P1',
        notification_type: 'LEGACY_P1',
        template_version: 'p1-unknown',
        provider_message_id: null,
        failure_reason: null,
        sent_at: null,
      })),
    );

    const [indexes] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT index_name AS idx, non_unique AS nu, column_name AS col
         FROM information_schema.statistics
        WHERE table_schema = ? AND table_name = 'notification_logs'
        ORDER BY index_name, seq_in_index`,
      [DATABASE],
    );
    expect(indexes).toEqual([
      { idx: 'PRIMARY', nu: 0, col: 'id' },
      { idx: 'uq_event_recipient_type', nu: 0, col: 'event_id' },
      { idx: 'uq_event_recipient_type', nu: 0, col: 'recipient_email' },
      { idx: 'uq_event_recipient_type', nu: 0, col: 'notification_type' },
      { idx: 'uq_notification_id', nu: 0, col: 'notification_id' },
    ]);
  });

  it('applies V3 after Java wrote Phase 1 rows between V2 and V3 (rollback case)', async () => {
    const database = 'notification_rollback_db';
    await pool.query(`CREATE DATABASE ${database}`);
    await runFlyway(network, database, '-target=2');

    // What Java inserts after V2: new columns left at their defaults, recipient_email = ''.
    for (const userId of ['buyer@test.local', 'seller@test.local']) {
      await pool.execute(
        `INSERT INTO ${database}.notification_logs
           (notification_id, event_id, user_id, channel, subject, body, status, retry_count)
         VALUES (?, 'evt-after-v2', ?, 'EMAIL', 'Contract has been signed', 'body', 'SENT', 0)`,
        [`after-v2-${userId}`, userId],
      );
    }
    const countBefore = await countRows(pool, database);

    await runFlyway(network, database);

    expect(await countRows(pool, database)).toBe(countBefore);
    const [rows] = await pool.query<mysql.RowDataPacket[]>(
      `SELECT recipient_email FROM ${database}.notification_logs ORDER BY id`,
    );
    expect(rows).toEqual([
      { recipient_email: 'legacy:buyer@test.local' },
      { recipient_email: 'legacy:seller@test.local' },
    ]);
  });

  it('accepts two recipients for the same event_id after V3', async () => {
    const repository = new NotificationLogRepository(pool);
    const base = {
      eventId: 'evt-signed-p2',
      eventType: 'contract.signed',
      notificationType: 'P1_CONTRACT_SIGNED',
      templateVersion: 'P1_CONTRACT_SIGNED_V1',
      subject: '[AgriContract] Contract has been signed',
      body: 'Hello',
    };

    const buyer = await repository.reserve({
      ...base,
      notificationId: 'p2-buyer',
      recipientUserId: 'buyer@test.local',
      recipientEmail: 'buyer@test.local',
    });
    const seller = await repository.reserve({
      ...base,
      notificationId: 'p2-seller',
      recipientUserId: 'seller@test.local',
      recipientEmail: 'seller@test.local',
    });

    expect([buyer, seller]).toEqual([true, true]);
    const [rows] = await pool.query<mysql.RowDataPacket[]>(
      'SELECT recipient_email, status FROM notification_logs WHERE event_id = ? ORDER BY id',
      ['evt-signed-p2'],
    );
    expect(rows).toEqual([
      { recipient_email: 'buyer@test.local', status: 'PENDING' },
      { recipient_email: 'seller@test.local', status: 'PENDING' },
    ]);
  });

  describe('NotificationLogRepository', () => {
    const otp = {
      eventId: 'req-otp-1',
      eventType: 'otp.sign',
      notificationType: 'OTP_SIGN',
      templateVersion: 'OTP_SIGN_V1',
      recipientUserId: 'user-42',
      recipientEmail: 'signer@test.local',
      subject: '[AgriContract] Signing code',
      body: 'Your code is 123456',
    };

    it('returns false when the dedup key is already reserved', async () => {
      const repository = new NotificationLogRepository(pool);

      expect(await repository.reserve({ ...otp, notificationId: 'otp-a' })).toBe(true);
      expect(await repository.reserve({ ...otp, notificationId: 'otp-b' })).toBe(false);
    });

    it('allows the same event and recipient with another notification type', async () => {
      const repository = new NotificationLogRepository(pool);

      const reserved = await repository.reserve({
        ...otp,
        notificationId: 'otp-other-type',
        notificationType: 'OTHER_TYPE',
        body: 'Not an OTP mail',
      });

      expect(reserved).toBe(true);
    });

    it('never stores the OTP mail body', async () => {
      const repository = new NotificationLogRepository(pool);

      const log = await repository.findByKey(otp);

      expect(log?.body).toBe('[redacted:OTP_SIGN_V1]');
      const [rows] = await pool.query<mysql.RowDataPacket[]>(
        "SELECT COUNT(*) AS n FROM notification_logs WHERE body LIKE '%123456%'",
      );
      expect(Number(rows[0]?.n)).toBe(0);
    });

    it('records provider message id and sent time on markSent', async () => {
      const repository = new NotificationLogRepository(pool);
      const sentAt = new Date('2026-10-16T03:04:05.678Z');

      expect(await repository.markSent('otp-a', '<msg-1@mailhog>', sentAt)).toBe(true);

      const log = await repository.findByKey(otp);
      expect(log).toMatchObject({
        notificationId: 'otp-a',
        recipientUserId: 'user-42',
        status: 'SENT',
        providerMessageId: '<msg-1@mailhog>',
        sentAt,
        retryCount: 0,
      });
      const [raw] = await pool.query<mysql.RowDataPacket[]>(
        "SELECT CAST(sent_at AS CHAR) AS sent_at FROM notification_logs WHERE notification_id = 'otp-a'",
      );
      expect(raw[0]?.sent_at).toBe('2026-10-16 03:04:05.678000');
    });

    it('records the reason and counts the attempt on markFailed', async () => {
      const repository = new NotificationLogRepository(pool);
      const key = { ...otp, notificationType: 'OTHER_TYPE' };

      expect(await repository.markFailed('otp-other-type', 'SMTP timeout')).toBe(true);

      expect(await repository.findByKey(key)).toMatchObject({
        status: 'FAILED',
        failureReason: 'SMTP timeout',
        retryCount: 1,
        sentAt: null,
      });
    });

    it('reports unknown rows', async () => {
      const repository = new NotificationLogRepository(pool);

      expect(await repository.findByKey({ ...otp, eventId: 'missing' })).toBeNull();
      expect(await repository.markSent('missing', 'x', new Date())).toBe(false);
      expect(await repository.markFailed('missing', 'x')).toBe(false);
    });
  });
});
