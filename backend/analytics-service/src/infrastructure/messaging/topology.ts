/**
 * RabbitMQ topology for analytics-service.
 *
 * Analytics is non-critical (analytics design §1, Verification Matrix #23):
 *  - producers (contract-service) publish to their own exchange and never wait for analytics;
 *  - each subscribed event gets a DURABLE queue bound to that exchange, so while analytics is
 *    down messages accumulate there and are consumed (caught up) when it comes back;
 *  - queues have NO length limit / reject-publish overflow, which could otherwise push back on
 *    producers; failed messages go to a per-queue DLQ (same pattern as the Java services).
 */

import { INGESTED_EVENT_TYPES } from '../../domain/ingestedEvents.js';

/** Exchange used by contract-service for contract.* and milestone.* events (see its OutboxPoller). */
export const CONTRACTS_EXCHANGE = 'agricontract.contracts';
export const ANALYTICS_DLX = 'agricontract.analytics.dlx';
export const QUEUE_PREFIX = 'analytics-svc';

export interface Subscription {
  eventType: string;
  exchange: string;
}

/** One durable queue per ingested event, bound to the exchange of the service that publishes it. */
export const SUBSCRIPTIONS: readonly Subscription[] = INGESTED_EVENT_TYPES.map((eventType) => ({
  eventType,
  exchange: CONTRACTS_EXCHANGE,
}));

export function queueName(eventType: string): string {
  return `${QUEUE_PREFIX}.${eventType}`;
}

export function deadLetterQueueName(eventType: string): string {
  return `${queueName(eventType)}.dlq`;
}

/** Arguments for a main queue. Kept explicit so a test can prove nothing here can block producers. */
export function queueArguments(): Record<string, unknown> {
  return { 'x-dead-letter-exchange': ANALYTICS_DLX };
}

/** Subset of an amqplib Channel used to declare topology. */
export interface TopologyChannel {
  assertExchange(exchange: string, type: string, options?: { durable?: boolean; autoDelete?: boolean }): Promise<unknown>;
  assertQueue(queue: string, options?: { durable?: boolean; arguments?: Record<string, unknown> }): Promise<unknown>;
  bindQueue(queue: string, source: string, pattern: string): Promise<unknown>;
}

/** Idempotent: safe to run on every start (RabbitMQ asserts are no-ops when already declared). */
export async function declareTopology(
  channel: TopologyChannel,
  subscriptions: readonly Subscription[] = SUBSCRIPTIONS,
): Promise<void> {
  // Same declaration as the Java services (TopicExchange durable=true, autoDelete=false).
  for (const exchange of new Set(subscriptions.map((s) => s.exchange))) {
    await channel.assertExchange(exchange, 'topic', { durable: true, autoDelete: false });
  }
  await channel.assertExchange(ANALYTICS_DLX, 'direct', { durable: true, autoDelete: false });

  for (const { eventType, exchange } of subscriptions) {
    const queue = queueName(eventType);
    const dlq = deadLetterQueueName(eventType);
    await channel.assertQueue(queue, { durable: true, arguments: queueArguments() });
    await channel.assertQueue(dlq, { durable: true });
    await channel.bindQueue(queue, exchange, eventType);
    // Dead-lettering keeps the original routing key (= eventType).
    await channel.bindQueue(dlq, ANALYTICS_DLX, eventType);
  }
}
