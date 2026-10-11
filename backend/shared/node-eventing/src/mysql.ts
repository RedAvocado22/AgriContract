import type { EventEnvelope } from './envelope.js';
import type { OutboxRow, OutboxStore, ProcessedEventStore, TransactionRunner } from './ports.js';

/**
 * MySQL 8 adapters (same engine as the Java services). Typed structurally against the
 * subset of `mysql2/promise` we use, so services pass in their own pool:
 *   const pool = mysql.createPool({ uri: process.env.DATABASE_URL });
 * Tables come from migrations/V1__create_eventing_tables.sql.
 */

/** Subset of a mysql2/promise PoolConnection. */
export interface MysqlConnectionLike {
  query(sql: string, values?: unknown[]): Promise<[unknown, unknown]>;
  beginTransaction(): Promise<void>;
  commit(): Promise<void>;
  rollback(): Promise<void>;
  release(): void;
}

/** Subset of a mysql2/promise Pool. */
export interface MysqlPoolLike {
  getConnection(): Promise<MysqlConnectionLike>;
}

export class MysqlTransactionRunner implements TransactionRunner<MysqlConnectionLike> {
  constructor(private readonly pool: MysqlPoolLike) {}

  async inTransaction<T>(work: (tx: MysqlConnectionLike) => Promise<T>): Promise<T> {
    const conn = await this.pool.getConnection();
    try {
      await conn.beginTransaction();
      const result = await work(conn);
      await conn.commit();
      return result;
    } catch (err) {
      try {
        await conn.rollback();
      } catch {
        // keep the original error
      }
      throw err;
    } finally {
      conn.release();
    }
  }
}

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/;

function tableName(name: string): string {
  if (!IDENTIFIER.test(name)) throw new Error(`Invalid table name: ${name}`);
  return `\`${name}\``;
}

interface OutboxDbRow {
  id: number | string;
  event_id: string;
  event_type: string;
  aggregate_id: string;
  envelope: unknown;
  attempts: number | string;
}

export class MysqlOutboxStore implements OutboxStore<MysqlConnectionLike> {
  private readonly table: string;

  constructor(options: { table?: string } = {}) {
    this.table = tableName(options.table ?? 'event_outbox');
  }

  async insert(tx: MysqlConnectionLike, envelope: EventEnvelope): Promise<void> {
    await tx.query(
      `INSERT INTO ${this.table} (event_id, event_type, aggregate_id, envelope, status) VALUES (?, ?, ?, ?, 'PENDING')`,
      [envelope.eventId, envelope.eventType, envelope.aggregateId, JSON.stringify(envelope)],
    );
  }

  async claimPending(tx: MysqlConnectionLike, limit: number): Promise<OutboxRow[]> {
    const [rows] = await tx.query(
      `SELECT id, event_id, event_type, aggregate_id, envelope, attempts FROM ${this.table}
       WHERE status = 'PENDING' ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED`,
      [Math.max(1, Math.floor(limit))],
    );
    return (rows as OutboxDbRow[]).map((r) => ({
      id: Number(r.id),
      eventId: r.event_id,
      eventType: r.event_type,
      aggregateId: r.aggregate_id,
      // mysql2 returns JSON columns already parsed; other drivers may return a string.
      envelope: (typeof r.envelope === 'string' ? JSON.parse(r.envelope) : r.envelope) as EventEnvelope,
      attempts: Number(r.attempts),
    }));
  }

  async markPublished(tx: MysqlConnectionLike, id: number): Promise<void> {
    await tx.query(`UPDATE ${this.table} SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3) WHERE id = ?`, [id]);
  }

  async recordFailure(tx: MysqlConnectionLike, id: number, error: string): Promise<void> {
    await tx.query(`UPDATE ${this.table} SET attempts = attempts + 1, last_error = ? WHERE id = ?`, [error, id]);
  }

  async countPendingOlderThan(tx: MysqlConnectionLike, olderThanMs: number): Promise<number> {
    const [rows] = await tx.query(
      `SELECT COUNT(*) AS c FROM ${this.table}
       WHERE status = 'PENDING' AND created_at < CURRENT_TIMESTAMP(3) - INTERVAL ? MICROSECOND`,
      [Math.max(0, Math.floor(olderThanMs * 1000))],
    );
    return Number((rows as Array<{ c: number | string }>)[0]?.c ?? 0);
  }
}

export class MysqlProcessedEventStore implements ProcessedEventStore<MysqlConnectionLike> {
  private readonly table: string;

  constructor(options: { table?: string } = {}) {
    this.table = tableName(options.table ?? 'processed_event');
  }

  async tryMarkProcessed(tx: MysqlConnectionLike, consumerName: string, envelope: EventEnvelope): Promise<boolean> {
    try {
      await tx.query(`INSERT INTO ${this.table} (consumer_name, event_id, event_type) VALUES (?, ?, ?)`, [
        consumerName,
        envelope.eventId,
        envelope.eventType,
      ]);
      return true;
    } catch (err) {
      if (isDuplicateKey(err)) return false;
      throw err;
    }
  }
}

export function isDuplicateKey(err: unknown): boolean {
  const e = err as { code?: unknown; errno?: unknown } | null;
  return !!e && (e.code === 'ER_DUP_ENTRY' || e.errno === 1062);
}
