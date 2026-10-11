import type { EventEnvelope } from '../src/envelope.js';
import type {
  EventPublisher,
  Logger,
  OutboxRow,
  OutboxStore,
  ProcessedEventStore,
  PublishMessage,
  TransactionRunner,
} from '../src/ports.js';

/** In-memory database with real commit/rollback semantics and serialised transactions. */
export interface DbState {
  nextId: number;
  outbox: Array<OutboxRow & { status: 'PENDING' | 'PUBLISHED'; lastError: string | null; createdAtMs: number }>;
  processed: Array<{ consumerName: string; eventId: string; eventType: string }>;
  /** Stand-in for a service's business table (e.g. analytics fact rows). */
  business: string[];
}

export type FakeTx = { state: DbState };

export class InMemoryDb implements TransactionRunner<FakeTx> {
  committed: DbState = { nextId: 1, outbox: [], processed: [], business: [] };
  private lock: Promise<void> = Promise.resolve();
  clockMs = Date.now();

  async inTransaction<T>(work: (tx: FakeTx) => Promise<T>): Promise<T> {
    const previous = this.lock;
    let release!: () => void;
    this.lock = new Promise((r) => (release = r));
    await previous;
    try {
      const tx: FakeTx = { state: structuredClone(this.committed) };
      const result = await work(tx);
      this.committed = tx.state; // commit
      return result;
    } finally {
      release(); // on throw the working copy is simply discarded (rollback)
    }
  }
}

export class FakeOutboxStore implements OutboxStore<FakeTx> {
  constructor(private readonly db: InMemoryDb) {}

  async insert(tx: FakeTx, envelope: EventEnvelope): Promise<void> {
    if (tx.state.outbox.some((r) => r.eventId === envelope.eventId)) {
      throw Object.assign(new Error('Duplicate entry'), { code: 'ER_DUP_ENTRY' });
    }
    tx.state.outbox.push({
      id: tx.state.nextId++,
      eventId: envelope.eventId,
      eventType: envelope.eventType,
      aggregateId: envelope.aggregateId,
      envelope: structuredClone(envelope),
      attempts: 0,
      status: 'PENDING',
      lastError: null,
      createdAtMs: this.db.clockMs,
    });
  }

  async claimPending(tx: FakeTx, limit: number): Promise<OutboxRow[]> {
    return tx.state.outbox
      .filter((r) => r.status === 'PENDING')
      .sort((a, b) => a.id - b.id)
      .slice(0, limit)
      .map((r) => ({ ...r, envelope: structuredClone(r.envelope) }));
  }

  async markPublished(tx: FakeTx, id: number): Promise<void> {
    const row = tx.state.outbox.find((r) => r.id === id);
    if (row) row.status = 'PUBLISHED';
  }

  async recordFailure(tx: FakeTx, id: number, error: string): Promise<void> {
    const row = tx.state.outbox.find((r) => r.id === id);
    if (row) {
      row.attempts += 1;
      row.lastError = error;
    }
  }

  async countPendingOlderThan(tx: FakeTx, olderThanMs: number): Promise<number> {
    const threshold = this.db.clockMs - olderThanMs;
    return tx.state.outbox.filter((r) => r.status === 'PENDING' && r.createdAtMs < threshold).length;
  }
}

export class FakeProcessedEventStore implements ProcessedEventStore<FakeTx> {
  async tryMarkProcessed(tx: FakeTx, consumerName: string, envelope: EventEnvelope): Promise<boolean> {
    if (tx.state.processed.some((p) => p.consumerName === consumerName && p.eventId === envelope.eventId)) {
      return false;
    }
    tx.state.processed.push({ consumerName, eventId: envelope.eventId, eventType: envelope.eventType });
    return true;
  }
}

export class FakePublisher implements EventPublisher {
  sent: PublishMessage[] = [];
  failNext = 0;
  onPublish?: (message: PublishMessage) => void;

  async publish(message: PublishMessage): Promise<void> {
    this.onPublish?.(message);
    if (this.failNext > 0) {
      this.failNext -= 1;
      throw new Error('broker unavailable');
    }
    this.sent.push(message);
  }
}

export const silentLogger: Logger = { debug() {}, info() {}, warn() {}, error() {} };
