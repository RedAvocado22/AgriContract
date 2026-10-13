import type {
  AmqpChannelLike,
  AmqpMessageLike,
  EventEnvelope,
  Logger,
  ProcessedEventStore,
  TransactionRunner,
} from '@agricontract/eventing';
import { createEnvelope } from '@agricontract/eventing';
import type { TopologyChannel } from '../src/infrastructure/messaging/topology.js';

/**
 * Minimal in-memory RabbitMQ: topic exchanges with exact routing keys, direct DLX,
 * durable queues that keep messages while nobody consumes, manual ack/nack with
 * dead-lettering, and prefetch (unacked messages per consumer).
 */
export class FakeBroker implements TopologyChannel, AmqpChannelLike {
  exchanges = new Map<string, { type: string; options: unknown }>();
  queues = new Map<string, { options: { durable?: boolean; arguments?: Record<string, unknown> }; messages: AmqpMessageLike[] }>();
  bindings: Array<{ queue: string; exchange: string; key: string }> = [];
  private consumers = new Map<string, { onMessage: (m: AmqpMessageLike | null) => void; unacked: number }>();
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
    this.consumers.set(queue, { onMessage, unacked: 0 });
    this.dispatch(queue);
    return { consumerTag: tag };
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
  facts: string[];
}
export type FakeTx = { state: DbState };

export class InMemoryDb implements TransactionRunner<FakeTx>, ProcessedEventStore<FakeTx> {
  committed: DbState = { processed: [], facts: [] };
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

export const silentLogger: Logger = { debug() {}, info() {}, warn() {}, error() {} };

export function event(eventType: string, aggregateId: string, payload: Record<string, unknown> = {}): EventEnvelope {
  return createEnvelope({
    eventType,
    producer: 'contract-service',
    aggregateId,
    correlationId: `saga-${aggregateId}`,
    payload: { contractId: aggregateId, ...payload },
  });
}

export function publishEvent(broker: FakeBroker, exchange: string, env: EventEnvelope): boolean {
  return broker.publish(exchange, env.eventType, Buffer.from(JSON.stringify(env)), env.eventId);
}
