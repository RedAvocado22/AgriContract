import type { Channel } from 'amqplib';

/*
 * RabbitMQ topology of notification-service, identical to the Java
 * RabbitMQConfig. These queues already exist on the broker: any option that
 * differs (durability, arguments, ...) makes RabbitMQ answer
 * PRECONDITION_FAILED and close the channel, so keep every option explicit.
 */

/** One row = one source exchange, its dead-letter exchange and routing keys. Phase 2 adds rows. */
export const TOPOLOGY = [
  {
    exchange: 'agricontract.contracts',
    dlx: 'notification-svc.contracts.dlx',
    routingKeys: [
      'contract.signed',
      'contract.delivered',
      'contract.cancelled',
      'contract.disputed',
    ],
  },
  {
    exchange: 'agricontract.escrow',
    dlx: 'notification-svc.escrow.dlx',
    routingKeys: ['escrow.locked', 'escrow.released', 'escrow.penalized'],
  },
  {
    exchange: 'agricontract.events',
    dlx: 'notification-svc.events.dlx',
    routingKeys: ['category.approved', 'category.rejected'],
  },
] as const;

export type RoutingKey = (typeof TOPOLOGY)[number]['routingKeys'][number];

export const queueName = (routingKey: RoutingKey): string =>
  `notification-svc.${routingKey}`;

export const dlqName = (routingKey: RoutingKey): string =>
  `${queueName(routingKey)}.dlq`;

const routingKeys = (): RoutingKey[] =>
  TOPOLOGY.flatMap((row) => [...row.routingKeys]);

export const queueNames = (): string[] => routingKeys().map(queueName);

export const dlqNames = (): string[] => routingKeys().map(dlqName);

export type TopologyChannel = Pick<
  Channel,
  'assertExchange' | 'assertQueue' | 'bindQueue'
>;

/** Declares exchanges, queues, DLQs and bindings exactly as the Java service does. */
export async function declareTopology(channel: TopologyChannel): Promise<void> {
  for (const row of TOPOLOGY) {
    await channel.assertExchange(row.exchange, 'topic', {
      durable: true,
      autoDelete: false,
    });
    await channel.assertExchange(row.dlx, 'direct', {
      durable: true,
      autoDelete: false,
    });

    for (const key of row.routingKeys) {
      const queue = queueName(key);
      const dlq = dlqName(key);
      await channel.assertQueue(queue, {
        durable: true,
        arguments: { 'x-dead-letter-exchange': row.dlx },
      });
      await channel.assertQueue(dlq, { durable: true });
      await channel.bindQueue(queue, row.exchange, key);
      // The DLX keeps the original routing key, so the DLQ binds with it.
      await channel.bindQueue(dlq, row.dlx, key);
    }
  }
}
