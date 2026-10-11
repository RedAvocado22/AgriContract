import type { EventEnvelope } from './envelope.js';

/**
 * Storage/broker ports. MySQL and AMQP adapters live in mysql.ts / amqp.ts;
 * tests use in-memory fakes. `Tx` is whatever transaction handle the adapter uses
 * (for MySQL: a mysql2/promise connection with an open transaction).
 */

export interface TransactionRunner<Tx> {
  /** Runs `work` in one DB transaction: commit on success, rollback on throw. */
  inTransaction<T>(work: (tx: Tx) => Promise<T>): Promise<T>;
}

export interface OutboxRow {
  id: number;
  eventId: string;
  eventType: string;
  aggregateId: string;
  envelope: EventEnvelope;
  attempts: number;
}

export interface OutboxStore<Tx> {
  /** Inserts a PENDING row inside the caller's business transaction. */
  insert(tx: Tx, envelope: EventEnvelope): Promise<void>;
  /** Locks up to `limit` PENDING rows in id order (MySQL: FOR UPDATE SKIP LOCKED). */
  claimPending(tx: Tx, limit: number): Promise<OutboxRow[]>;
  /** Marks the row PUBLISHED, stamping published_at with the DB clock. */
  markPublished(tx: Tx, id: number): Promise<void>;
  recordFailure(tx: Tx, id: number, error: string): Promise<void>;
  /** PENDING rows created more than `olderThanMs` ago (measured with the DB clock). */
  countPendingOlderThan(tx: Tx, olderThanMs: number): Promise<number>;
}

export interface ProcessedEventStore<Tx> {
  /**
   * Records that `consumerName` handled `eventId`. Returns false when it was already
   * recorded (duplicate delivery / DLQ replay) — the caller must then skip the handler.
   */
  tryMarkProcessed(tx: Tx, consumerName: string, envelope: EventEnvelope): Promise<boolean>;
}

export interface PublishMessage {
  exchange: string;
  routingKey: string;
  body: Buffer;
  messageId: string;
  type: string;
  timestamp: number;
  headers: Record<string, string>;
}

export interface EventPublisher {
  /** Resolves only after the broker confirmed the message (publisher confirms). */
  publish(message: PublishMessage): Promise<void>;
}

export interface Logger {
  debug(message: string, meta?: Record<string, unknown>): void;
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
}

export const consoleLogger: Logger = {
  debug: (m, meta) => console.debug(m, meta ?? ''),
  info: (m, meta) => console.info(m, meta ?? ''),
  warn: (m, meta) => console.warn(m, meta ?? ''),
  error: (m, meta) => console.error(m, meta ?? ''),
};
