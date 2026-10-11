import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { AmqpConfirmPublisher, consumeEvents, type AmqpPublishOptions } from '../src/amqp.js';
import { createEnvelope } from '../src/envelope.js';
import {
  MysqlOutboxStore,
  MysqlProcessedEventStore,
  MysqlTransactionRunner,
  type MysqlConnectionLike,
} from '../src/mysql.js';
import { toPublishMessage } from '../src/outbox.js';

class RecordingConnection implements MysqlConnectionLike {
  calls: Array<{ sql: string; values?: unknown[] }> = [];
  log: string[] = [];
  nextRows: unknown = [];
  nextError: unknown = undefined;

  async query(sql: string, values?: unknown[]): Promise<[unknown, unknown]> {
    this.calls.push({ sql, values });
    if (this.nextError) {
      const e = this.nextError;
      this.nextError = undefined;
      throw e;
    }
    return [this.nextRows, []];
  }
  async beginTransaction() {
    this.log.push('begin');
  }
  async commit() {
    this.log.push('commit');
  }
  async rollback() {
    this.log.push('rollback');
  }
  release() {
    this.log.push('release');
  }
}

const env = createEnvelope({
  eventType: 'file.ready',
  producer: 'file-service',
  aggregateId: 'f-1',
  payload: { fileId: 'f-1' },
});

describe('MysqlTransactionRunner', () => {
  it('commits and releases on success', async () => {
    const conn = new RecordingConnection();
    const runner = new MysqlTransactionRunner({ getConnection: async () => conn });
    assert.equal(await runner.inTransaction(async () => 42), 42);
    assert.deepEqual(conn.log, ['begin', 'commit', 'release']);
  });

  it('rolls back, releases and rethrows on failure', async () => {
    const conn = new RecordingConnection();
    const runner = new MysqlTransactionRunner({ getConnection: async () => conn });
    await assert.rejects(
      runner.inTransaction(async () => {
        throw new Error('boom');
      }),
      /boom/,
    );
    assert.deepEqual(conn.log, ['begin', 'rollback', 'release']);
  });
});

describe('MysqlOutboxStore', () => {
  it('inserts the full envelope as JSON with PENDING status', async () => {
    const conn = new RecordingConnection();
    await new MysqlOutboxStore().insert(conn, env);
    const call = conn.calls[0];
    assert.match(call!.sql, /INSERT INTO `event_outbox`/);
    assert.deepEqual(call!.values?.slice(0, 3), [env.eventId, env.eventType, env.aggregateId]);
    assert.deepEqual(JSON.parse(call!.values?.[3] as string), env);
  });

  it('claims PENDING rows in id order with SKIP LOCKED and parses JSON from either driver shape', async () => {
    const conn = new RecordingConnection();
    conn.nextRows = [
      { id: '7', event_id: env.eventId, event_type: env.eventType, aggregate_id: 'f-1', envelope: JSON.stringify(env), attempts: '2' },
      { id: 8, event_id: env.eventId, event_type: env.eventType, aggregate_id: 'f-1', envelope: env, attempts: 0 },
    ];
    const rows = await new MysqlOutboxStore().claimPending(conn, 10);
    assert.match(conn.calls[0]!.sql, /WHERE status = 'PENDING' ORDER BY id LIMIT \? FOR UPDATE SKIP LOCKED/);
    assert.deepEqual(conn.calls[0]!.values, [10]);
    assert.equal(rows[0]!.id, 7);
    assert.equal(rows[0]!.attempts, 2);
    assert.deepEqual(rows[0]!.envelope, env);
    assert.deepEqual(rows[1]!.envelope, env);
  });

  it('supports a custom table name but rejects SQL injection', () => {
    assert.doesNotThrow(() => new MysqlOutboxStore({ table: 'analytics_outbox' }));
    assert.throws(() => new MysqlOutboxStore({ table: 'x; DROP TABLE y' }));
  });

  it('marks published and counts stale rows using the DB clock', async () => {
    const conn = new RecordingConnection();
    const store = new MysqlOutboxStore();
    await store.markPublished(conn, 7);
    assert.match(conn.calls[0]!.sql, /status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP\(3\)/);
    conn.nextRows = [{ c: '3' }];
    assert.equal(await store.countPendingOlderThan(conn, 300_000), 3);
    assert.deepEqual(conn.calls[1]!.values, [300_000_000]);
  });
});

describe('MysqlProcessedEventStore', () => {
  it('returns true when the marker row is inserted', async () => {
    const conn = new RecordingConnection();
    assert.equal(await new MysqlProcessedEventStore().tryMarkProcessed(conn, 'c1', env), true);
    assert.deepEqual(conn.calls[0]!.values, ['c1', env.eventId, env.eventType]);
  });

  it('returns false on duplicate key (already processed)', async () => {
    const conn = new RecordingConnection();
    conn.nextError = Object.assign(new Error("Duplicate entry 'c1-...' for key 'PRIMARY'"), {
      code: 'ER_DUP_ENTRY',
      errno: 1062,
    });
    assert.equal(await new MysqlProcessedEventStore().tryMarkProcessed(conn, 'c1', env), false);
  });

  it('rethrows any other database error', async () => {
    const conn = new RecordingConnection();
    conn.nextError = Object.assign(new Error('Lock wait timeout'), { code: 'ER_LOCK_WAIT_TIMEOUT', errno: 1205 });
    await assert.rejects(new MysqlProcessedEventStore().tryMarkProcessed(conn, 'c1', env), /Lock wait timeout/);
  });
});

describe('AmqpConfirmPublisher', () => {
  it('publishes persistent JSON with messageId/type and resolves on broker ack', async () => {
    let seen: { exchange: string; routingKey: string; options: AmqpPublishOptions } | undefined;
    const publisher = new AmqpConfirmPublisher({
      publish(exchange, routingKey, _content, options, cb) {
        seen = { exchange, routingKey, options };
        setImmediate(() => cb(null));
        return true;
      },
    });
    const row = { id: 1, eventId: env.eventId, eventType: env.eventType, aggregateId: 'f-1', envelope: env, attempts: 0 };
    await publisher.publish(toPublishMessage('agricontract.files', row));
    assert.equal(seen?.exchange, 'agricontract.files');
    assert.equal(seen?.routingKey, 'file.ready');
    assert.equal(seen?.options.persistent, true);
    assert.equal(seen?.options.contentType, 'application/json');
    assert.equal(seen?.options.messageId, env.eventId);
    assert.equal(seen?.options.type, 'file.ready');
  });

  it('rejects when the broker nacks', async () => {
    const publisher = new AmqpConfirmPublisher({
      publish(_e, _r, _c, _o, cb) {
        setImmediate(() => cb(new Error('nack')));
        return true;
      },
    });
    const row = { id: 1, eventId: env.eventId, eventType: env.eventType, aggregateId: 'f-1', envelope: env, attempts: 0 };
    await assert.rejects(publisher.publish(toPublishMessage('x', row)), /nack/);
  });
});

describe('consumeEvents', () => {
  it('subscribes with manual acks and ignores broker cancel (null message)', async () => {
    let registered: ((m: null) => void) | undefined;
    let opts: { noAck?: boolean } | undefined;
    const tag = await consumeEvents({
      channel: {
        async consume(_q, onMessage, o) {
          registered = onMessage as (m: null) => void;
          opts = o;
          return { consumerTag: 'ctag-1' };
        },
        ack() {},
        nack() {},
      },
      queue: 'analytics-svc.contract.signed',
      consumer: { consumerName: 'x', handleMessage: async () => ({ status: 'PROCESSED', envelope: env }) },
    });
    assert.equal(tag, 'ctag-1');
    assert.equal(opts?.noAck, false);
    assert.doesNotThrow(() => registered?.(null));
  });
});
