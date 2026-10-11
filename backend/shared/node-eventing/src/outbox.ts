import { assertValidEnvelope, type EventEnvelope } from './envelope.js';
import {
  consoleLogger,
  type EventPublisher,
  type Logger,
  type OutboxRow,
  type OutboxStore,
  type PublishMessage,
  type TransactionRunner,
} from './ports.js';

/**
 * Transactional outbox, Node equivalent of the Java OutboxPoller/OutboxMonitor.
 *
 * Rule: an event is NEVER published directly. The producer writes it to the outbox in
 * the same DB transaction as its business change (`enqueue`); only the poller publishes,
 * and only rows that are already committed. A row becomes PUBLISHED only after the broker
 * confirms it, so a crash/broker outage means "publish again", never "lost event".
 * Delivery is therefore at-least-once; consumers dedupe by eventId (see dedupe.ts).
 */
export class Outbox<Tx> {
  constructor(private readonly store: OutboxStore<Tx>) {}

  /** Persist `envelope` inside the caller's open transaction `tx`. */
  async enqueue(tx: Tx, envelope: EventEnvelope): Promise<void> {
    assertValidEnvelope(envelope);
    await this.store.insert(tx, envelope);
  }
}

export interface OutboxPollerOptions<Tx> {
  store: OutboxStore<Tx>;
  transactions: TransactionRunner<Tx>;
  publisher: EventPublisher;
  /** Exchange this service publishes to, e.g. "agricontract.analytics". Routing key = eventType. */
  exchange: string;
  batchSize?: number;
  pollIntervalMs?: number;
  /** PENDING rows older than this are reported by the monitor (Java: 5 minutes). */
  staleThresholdMs?: number;
  monitorIntervalMs?: number;
  logger?: Logger;
}

export interface PollResult {
  published: number;
  failed: number;
}

export class OutboxPoller<Tx> {
  private readonly batchSize: number;
  private readonly pollIntervalMs: number;
  private readonly staleThresholdMs: number;
  private readonly monitorIntervalMs: number;
  private readonly logger: Logger;
  private pollTimer: NodeJS.Timeout | undefined;
  private monitorTimer: NodeJS.Timeout | undefined;
  private running = false;
  private inFlight: Promise<unknown> | undefined;

  constructor(private readonly options: OutboxPollerOptions<Tx>) {
    this.batchSize = options.batchSize ?? 50;
    this.pollIntervalMs = options.pollIntervalMs ?? 1000;
    this.staleThresholdMs = options.staleThresholdMs ?? 5 * 60_000;
    this.monitorIntervalMs = options.monitorIntervalMs ?? 5 * 60_000;
    this.logger = options.logger ?? consoleLogger;
  }

  /**
   * Publishes one batch of committed PENDING rows in id order. Rows stay locked
   * (SKIP LOCKED) for the duration so several service instances never publish the
   * same row concurrently. Stops at the first broker failure to keep per-service order.
   */
  async pollOnce(): Promise<PollResult> {
    const { store, transactions, publisher, exchange } = this.options;
    return transactions.inTransaction(async (tx) => {
      const rows = await store.claimPending(tx, this.batchSize);
      let published = 0;
      for (const row of rows) {
        try {
          await publisher.publish(toPublishMessage(exchange, row));
        } catch (err) {
          const message = err instanceof Error ? err.message : String(err);
          await store.recordFailure(tx, row.id, message.slice(0, 1000));
          this.logger.warn('Outbox publish failed, will retry next poll', {
            eventId: row.eventId,
            eventType: row.eventType,
            attempts: row.attempts + 1,
            error: message,
          });
          return { published, failed: 1 };
        }
        await store.markPublished(tx, row.id);
        published += 1;
        this.logger.debug('Outbox event published', { eventId: row.eventId, eventType: row.eventType, exchange });
      }
      return { published, failed: 0 };
    });
  }

  /** Number of PENDING rows older than the stale threshold; logs a warning when > 0. */
  async checkStale(): Promise<number> {
    const count = await this.options.transactions.inTransaction((tx) =>
      this.options.store.countPendingOlderThan(tx, this.staleThresholdMs),
    );
    if (count > 0) {
      this.logger.warn('Outbox events stuck PENDING — need manual check', {
        count,
        staleThresholdMs: this.staleThresholdMs,
      });
    }
    return count;
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    const loop = async (): Promise<void> => {
      if (!this.running) return;
      this.inFlight = this.pollOnce().catch((err) =>
        this.logger.error('Outbox poll crashed', { error: err instanceof Error ? err.message : String(err) }),
      );
      await this.inFlight;
      if (this.running) this.pollTimer = setTimeout(loop, this.pollIntervalMs);
    };
    void loop();
    this.monitorTimer = setInterval(() => {
      this.checkStale().catch((err) =>
        this.logger.error('Outbox monitor failed', { error: err instanceof Error ? err.message : String(err) }),
      );
    }, this.monitorIntervalMs);
    this.monitorTimer.unref?.();
  }

  /** Stops scheduling and waits for the batch in progress to finish. */
  async stop(): Promise<void> {
    this.running = false;
    if (this.pollTimer) clearTimeout(this.pollTimer);
    if (this.monitorTimer) clearInterval(this.monitorTimer);
    await this.inFlight;
  }
}

export function toPublishMessage(exchange: string, row: OutboxRow): PublishMessage {
  const envelope = row.envelope;
  return {
    exchange,
    routingKey: envelope.eventType,
    body: Buffer.from(JSON.stringify(envelope), 'utf8'),
    messageId: envelope.eventId,
    type: envelope.eventType,
    timestamp: Math.floor(Date.parse(envelope.occurredAt) / 1000),
    headers: {
      'x-event-version': String(envelope.eventVersion),
      'x-producer': envelope.producer,
    },
  };
}
