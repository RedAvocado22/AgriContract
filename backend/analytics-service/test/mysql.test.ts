/**
 * Dedupe through a real MySQL: the analytics migration, the shared MysqlProcessedEventStore and
 * MysqlTransactionRunner, and the ingest use case together. Runs only when ANALYTICS_TEST_MYSQL_URL
 * is set, e.g. with the docker-compose MySQL (the database must exist; migrations are applied here):
 *   ANALYTICS_TEST_MYSQL_URL=mysql://root:root@localhost:3306/analytics_test npm test
 */
import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import mysql from 'mysql2/promise';
import {
  MysqlProcessedEventStore,
  MysqlTransactionRunner,
  type MysqlConnectionLike,
} from '@agricontract/eventing';
import { IngestEvent } from '../src/application/usecase/ingestEvent.js';
import { buildConsumer } from '../src/infrastructure/messaging/consumers.js';
import { MysqlIngestedEventStore } from '../src/infrastructure/persistence/ingestedEventStore.js';
import { applyMigrations } from '../src/infrastructure/persistence/migrate.js';
import { event } from './fakes.js';

const URL = process.env.ANALYTICS_TEST_MYSQL_URL;
const PROBE = 'it_projection_probe'; // test-only stand-in for a projection table

describe('real MySQL (ANALYTICS_TEST_MYSQL_URL)', { skip: URL ? false : 'ANALYTICS_TEST_MYSQL_URL not set' }, () => {
  let pool: mysql.Pool;

  before(async () => {
    const conn = await mysql.createConnection({ uri: URL!, multipleStatements: true });
    try {
      await applyMigrations(conn);
      await conn.query(`CREATE TABLE IF NOT EXISTS ${PROBE} (event_id CHAR(36) NOT NULL)`);
    } finally {
      await conn.end();
    }
    pool = mysql.createPool({ uri: URL!, connectionLimit: 4, dateStrings: true, timezone: 'Z' });
  });

  after(async () => {
    await pool?.end();
  });

  const count = async (sql: string, values: unknown[]) => {
    const [rows] = await pool.query(sql, values);
    return Number((rows as Array<{ c: number }>)[0]!.c);
  };

  /** The production consumer (validation, dedupe, landing table) plus a probe projection in the same transaction. */
  function consumer(failProjection = 0) {
    let failures = failProjection;
    const ingest = new IngestEvent<MysqlConnectionLike>(new MysqlIngestedEventStore(), [
      {
        eventType: 'milestone.settled',
        async apply(command, tx) {
          await tx.query(`INSERT INTO ${PROBE} (event_id) VALUES (?)`, [command.eventId]);
          if (failures > 0) {
            failures -= 1;
            throw new Error('projection failed');
          }
        },
      },
    ]);
    return buildConsumer<MysqlConnectionLike>(
      'milestone.settled',
      ingest,
      new MysqlTransactionRunner(pool),
      new MysqlProcessedEventStore({ table: 'analytics_idempotency_log' }),
    );
  }

  const body = (env: unknown) => Buffer.from(JSON.stringify(env));

  it('the same eventId twice: processed once, then skipped; one log row, one stored event, one projection row', async () => {
    const env = event('milestone.settled', 'm-it-1');
    const c = consumer();
    assert.equal((await c.handleMessage(body(env))).status, 'PROCESSED');
    assert.equal((await c.handleMessage(body(env))).status, 'DUPLICATE');
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_idempotency_log WHERE event_id = ?', [env.eventId]), 1);
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_ingested_event WHERE event_id = ?', [env.eventId]), 1);
    assert.equal(await count(`SELECT COUNT(*) AS c FROM ${PROBE} WHERE event_id = ?`, [env.eventId]), 1);
  });

  it('a failing projection rolls back the log row, the stored event and its writes; the retry applies it once', async () => {
    const env = event('milestone.settled', 'm-it-2');
    const c = consumer(1);
    await assert.rejects(c.handleMessage(body(env)), /projection failed/);
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_idempotency_log WHERE event_id = ?', [env.eventId]), 0);
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_ingested_event WHERE event_id = ?', [env.eventId]), 0);
    assert.equal(await count(`SELECT COUNT(*) AS c FROM ${PROBE} WHERE event_id = ?`, [env.eventId]), 0);
    assert.equal((await c.handleMessage(body(env))).status, 'PROCESSED');
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_ingested_event WHERE event_id = ?', [env.eventId]), 1);
  });

  it('the log row records consumer and event type; the stored event has no e-mail address', async () => {
    const env = event('milestone.settled', 'm-it-3');
    await consumer().handleMessage(body(env));
    const [rows] = await pool.query(
      'SELECT consumer_name, event_type FROM analytics_idempotency_log WHERE event_id = ?',
      [env.eventId],
    );
    assert.deepEqual(rows, [{ consumer_name: 'analytics.milestone.settled', event_type: 'milestone.settled' }]);
    const [stored] = await pool.query('SELECT payload FROM analytics_ingested_event WHERE event_id = ?', [env.eventId]);
    assert.doesNotMatch(JSON.stringify(stored), /email|@example\.com/);
  });

  it('an invalid payload never reaches the database', async () => {
    const env = event('milestone.settled', 'm-it-4', { lockedAmount: 'not-money' });
    await assert.rejects(consumer().handleMessage(body(env)), /Invalid milestone\.settled event/);
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_idempotency_log WHERE event_id = ?', [env.eventId]), 0);
    assert.equal(await count('SELECT COUNT(*) AS c FROM analytics_ingested_event WHERE event_id = ?', [env.eventId]), 0);
  });
});
