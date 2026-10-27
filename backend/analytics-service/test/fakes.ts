import type {
  AmqpChannelLike,
  AmqpMessageLike,
  EventEnvelope,
  Logger,
  ProcessedEventStore,
  TransactionRunner,
} from '@agricontract/eventing';
import type {
  ContractDimensionRepository,
  ContractOutcomeFactRepository,
  IngestEventCommand,
  IngestedEventStore,
  MilestoneFactRepository,
} from '../src/application/port';
import type {
  ContractDimension,
  ContractSettlementFact,
  ContractTerminationFact,
  MilestoneFact,
} from '../src/domain/projections';
import type { TopologyChannel } from '../src/infrastructure/messaging/topology';
import { validEvent } from './fixtures';

/**
 * Minimal in-memory RabbitMQ: topic exchanges with exact routing keys, direct DLX,
 * durable queues that keep messages while nobody consumes, manual ack/nack with
 * dead-lettering, and prefetch (unacked messages per consumer).
 */
export class FakeBroker implements TopologyChannel, AmqpChannelLike {
  exchanges = new Map<string, { type: string; options: unknown }>();
  queues = new Map<string, { options: { durable?: boolean; arguments?: Record<string, unknown> }; messages: AmqpMessageLike[] }>();
  bindings: Array<{ queue: string; exchange: string; key: string }> = [];
  private consumers = new Map<string, { tag: string; onMessage: (m: AmqpMessageLike | null) => void; unacked: number }>();
  private inflight = new Map<AmqpMessageLike, string>();
  private tagSeq = 0;

  constructor(private readonly prefetch = 1) {}

  async assertExchange(name: string, type: string, options?: unknown) {
    this.exchanges.set(name, { type, options });
    return {};
  }

  async assertQueue(name: string, options: { durable?: boolean; arguments?: Record<string, unknown> } = {}) {
    if (!this.queues.has(name)) this.queues.set(name, { options, messages: [] });
    return {};
  }

  async bindQueue(queue: string, exchange: string, key: string) {
    if (!this.bindings.some((b) => b.queue === queue && b.exchange === exchange && b.key === key)) {
      this.bindings.push({ queue, exchange, key });
    }
    return {};
  }

  /** Producer side: never blocks and never depends on whether a consumer is running. */
  publish(exchange: string, routingKey: string, body: Buffer, messageId?: string): boolean {
    for (const b of this.bindings.filter((x) => x.exchange === exchange && x.key === routingKey)) {
      this.enqueue(b.queue, { content: body, properties: { messageId }, fields: { routingKey } });
    }
    return true;
  }

  async consume(queue: string, onMessage: (m: AmqpMessageLike | null) => void) {
    const tag = `ctag-${++this.tagSeq}`;
    this.consumers.set(queue, { tag, onMessage, unacked: 0 });
    this.dispatch(queue);
    return { consumerTag: tag };
  }

  /** Cancels a consumer: no new deliveries; messages it already holds stay unacked until settled. */
  async cancel(consumerTag: string) {
    for (const [queue, c] of this.consumers) if (c.tag === consumerTag) this.consumers.delete(queue);
    return {};
  }

  /** Like amqplib checkQueue: depth of a queue that exists. */
  checkCalls = 0;
  async checkQueue(queue: string) {
    this.checkCalls += 1;
    if (!this.queues.has(queue)) throw new Error(`NOT_FOUND - no queue '${queue}'`);
    return { queue, messageCount: this.depth(queue), consumerCount: this.consumers.has(queue) ? 1 : 0 };
  }

  /** Number of messages delivered to a consumer and not yet acked or nacked. */
  unackedCount(): number {
    return this.inflight.size;
  }

  ack(message: AmqpMessageLike): void {
    this.settle(message);
  }

  nack(message: AmqpMessageLike, _allUpTo?: boolean, requeue?: boolean): void {
    const queue = this.settle(message);
    if (requeue) {
      this.enqueue(queue, message);
      return;
    }
    const dlx = this.queues.get(queue)?.options.arguments?.['x-dead-letter-exchange'] as string | undefined;
    if (dlx) this.publish(dlx, message.fields?.routingKey ?? '', message.content, message.properties?.messageId as string);
  }

  depth(queue: string): number {
    return this.queues.get(queue)?.messages.length ?? 0;
  }

  /** Resolves when every consumed queue is empty and nothing is in flight. */
  async drain(timeoutMs = 2000): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      const busy =
        this.inflight.size > 0 || [...this.consumers.keys()].some((q) => this.depth(q) > 0);
      if (!busy) return;
      await new Promise((r) => setTimeout(r, 2));
    }
    throw new Error('broker did not drain');
  }

  private enqueue(queue: string, message: AmqpMessageLike) {
    this.queues.get(queue)?.messages.push(message);
    this.dispatch(queue);
  }

  private settle(message: AmqpMessageLike): string {
    const queue = this.inflight.get(message);
    if (!queue) throw new Error('ack/nack of an unknown or already settled message');
    this.inflight.delete(message);
    const c = this.consumers.get(queue);
    if (c) c.unacked -= 1;
    setImmediate(() => this.dispatch(queue));
    return queue;
  }

  private dispatch(queue: string) {
    const c = this.consumers.get(queue);
    const q = this.queues.get(queue);
    if (!c || !q) return;
    while (c.unacked < this.prefetch && q.messages.length > 0) {
      const m = q.messages.shift()!;
      c.unacked += 1;
      this.inflight.set(m, queue);
      c.onMessage(m);
    }
  }
}

/** In-memory DB with real commit/rollback and serialized transactions. */
export interface DbState {
  processed: Array<{ consumerName: string; eventId: string; eventType: string }>;
  ingested: Array<{ eventId: string; eventType: string; aggregateId: string; payload: Record<string, unknown> }>;
  dimContract: ContractDimension[];
  milestoneFacts: MilestoneFact[];
  pendingForceMajeure: Array<{ milestoneId: string; flaggedAt: Date }>;
  contractTerminations: ContractTerminationFact[];
  contractSettlements: ContractSettlementFact[];
}
export type FakeTx = { state: DbState };

export class InMemoryDb implements TransactionRunner<FakeTx>, ProcessedEventStore<FakeTx> {
  committed: DbState = {
    processed: [],
    ingested: [],
    dimContract: [],
    milestoneFacts: [],
    pendingForceMajeure: [],
    contractTerminations: [],
    contractSettlements: [],
  };
  private lock: Promise<void> = Promise.resolve();

  async inTransaction<T>(work: (tx: FakeTx) => Promise<T>): Promise<T> {
    const previous = this.lock;
    let release!: () => void;
    this.lock = new Promise((r) => (release = r));
    await previous;
    try {
      const tx: FakeTx = { state: structuredClone(this.committed) };
      const result = await work(tx);
      this.committed = tx.state;
      return result;
    } finally {
      release();
    }
  }

  async tryMarkProcessed(tx: FakeTx, consumerName: string, envelope: EventEnvelope): Promise<boolean> {
    if (tx.state.processed.some((p) => p.consumerName === consumerName && p.eventId === envelope.eventId)) return false;
    tx.state.processed.push({ consumerName, eventId: envelope.eventId, eventType: envelope.eventType });
    return true;
  }
}

/** dim_contract fake: primary key contract_id, upsert overwrites. */
export class InMemoryContractDimension implements ContractDimensionRepository<FakeTx> {
  async upsert(tx: FakeTx, contract: ContractDimension): Promise<void> {
    const i = tx.state.dimContract.findIndex((c) => c.contractId === contract.contractId);
    if (i >= 0) tx.state.dimContract[i] = contract;
    else tx.state.dimContract.push(contract);
  }
}

/** fact_milestone_performance + pending_force_majeure_flag fake, with the unique keys of the real tables. */
export class InMemoryMilestoneFacts implements MilestoneFactRepository<FakeTx> {
  /** Every repository call in order, so tests can assert that the lock is taken first. */
  calls: string[] = [];
  async lockMilestone(_tx: FakeTx, milestoneId: string): Promise<void> {
    this.calls.push(`lock:${milestoneId}`);
  }
  async insertIfAbsent(tx: FakeTx, fact: MilestoneFact): Promise<boolean> {
    if (tx.state.milestoneFacts.some((f) => f.milestoneId === fact.milestoneId || f.factId === fact.factId)) return false;
    tx.state.milestoneFacts.push(fact);
    return true;
  }
  async markForceMajeure(tx: FakeTx, milestoneId: string): Promise<boolean> {
    const row = tx.state.milestoneFacts.find((f) => f.milestoneId === milestoneId);
    if (!row) return false;
    row.hasForceMajeure = true;
    return true;
  }
  async addPendingForceMajeure(tx: FakeTx, milestoneId: string, flaggedAt: Date): Promise<void> {
    if (!tx.state.pendingForceMajeure.some((p) => p.milestoneId === milestoneId)) {
      tx.state.pendingForceMajeure.push({ milestoneId, flaggedAt });
    }
  }
  async hasPendingForceMajeure(tx: FakeTx, milestoneId: string): Promise<boolean> {
    return tx.state.pendingForceMajeure.some((p) => p.milestoneId === milestoneId);
  }
  async deletePendingForceMajeure(tx: FakeTx, milestoneId: string): Promise<void> {
    tx.state.pendingForceMajeure = tx.state.pendingForceMajeure.filter((p) => p.milestoneId !== milestoneId);
  }
}

/** fact_contract_termination + fact_contract_settlement fake, with the unique keys of the real tables. */
export class InMemoryContractOutcomeFacts implements ContractOutcomeFactRepository<FakeTx> {
  async insertTerminationIfAbsent(tx: FakeTx, fact: ContractTerminationFact): Promise<boolean> {
    const rows = tx.state.contractTerminations;
    if (rows.some((f) => f.contractId === fact.contractId || f.factId === fact.factId)) return false;
    rows.push(fact);
    return true;
  }
  async insertSettlementIfAbsent(tx: FakeTx, fact: ContractSettlementFact): Promise<boolean> {
    const rows = tx.state.contractSettlements;
    if (rows.some((f) => f.contractId === fact.contractId || f.factId === fact.factId)) return false;
    rows.push(fact);
    return true;
  }
}

/** Landing-table fake: a duplicate event_id is a primary key violation, like the real table. */
export class InMemoryIngestedEventStore implements IngestedEventStore<FakeTx> {
  calls = 0;
  constructor(private failTimes = 0) {}

  async record(tx: FakeTx, command: IngestEventCommand): Promise<void> {
    this.calls += 1;
    if (tx.state.ingested.some((e) => e.eventId === command.eventId)) throw new Error('Duplicate entry for PRIMARY');
    tx.state.ingested.push({
      eventId: command.eventId,
      eventType: command.eventType,
      aggregateId: command.aggregateId,
      payload: command.payload as Record<string, unknown>,
    });
    if (this.failTimes > 0) {
      this.failTimes -= 1;
      throw new Error('deadlock');
    }
  }
}

/** Logger that keeps what it was asked to log, for assertions. */
export interface LogEntry {
  level: 'debug' | 'info' | 'warn' | 'error';
  msg: string;
  meta?: Record<string, unknown> | undefined;
}
export function recordingLogger(): Logger & { entries: LogEntry[] } {
  const entries: LogEntry[] = [];
  const make = (level: LogEntry['level']) => (msg: string, meta?: Record<string, unknown>) => {
    entries.push({ level, msg, meta });
  };
  return { entries, debug: make('debug'), info: make('info'), warn: make('warn'), error: make('error') };
}

export const silentLogger: Logger = { debug() {}, info() {}, warn() {}, error() {} };

/** A schema-valid canonical event of `eventType` (contract-service producer); payload fields can be overridden. */
export function event(eventType: string, aggregateId: string, payload: Record<string, unknown> = {}): EventEnvelope {
  return validEvent(eventType, aggregateId, { payload: { ...(validEvent(eventType).payload), ...payload } });
}

export function publishEvent(broker: FakeBroker, exchange: string, env: EventEnvelope): boolean {
  return broker.publish(exchange, env.eventType, Buffer.from(JSON.stringify(env)), env.eventId);
}
