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
      'notification.contract_anchor_requested',
      'notification.activation_cancel_requested',
      'notification.contract_activation_failed_requested',
      'notification.contract_terminated_requested',
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
  {
    // user-service's outbox exchange (agricontract.eventing.outbox.exchange).
    exchange: 'agricontract.user',
    dlx: 'notification-svc.user.dlx',
    routingKeys: ['notification.user_kyc_result_requested'],
  },
] as const;

export type RoutingKey = (typeof TOPOLOGY)[number]['routingKeys'][number];

/**
 * Phase 2 notification commands: a full event envelope whose
 * `payload.recipients` carries the addresses (notification-commands.yaml),
 * not a flat Phase 1 event. They ride their publisher's exchange and are
 * routed by their eventType (design §2.1), so the commands of
 * agricontract.contracts share one TOPOLOGY row and one DLX.
 *
 * `notification.contract_anchor_requested` is the signed-contract email.
 * contract-service already writes it to its outbox (#360) and declares this
 * queue, its DLQ and the DLX ahead of us (ConsumerQueuesDeclaredAhead), so
 * the names and options here must stay byte-identical to that class.
 *
 * `notification.activation_cancel_requested` (D42) tells the other party a
 * cancel was requested on a contract whose deposit lock failed. Declared ahead
 * the same way (CANCEL_REQUESTED_QUEUE), same DLX.
 *
 * `notification.contract_activation_failed_requested` (D42) tells both
 * parties the deposits of a signed contract could not be locked. Declared
 * ahead the same way (ACTIVATION_FAILED_QUEUE), same DLX.
 *
 * `notification.contract_terminated_requested` (D43) tells the other party an
 * offer was withdrawn; the other termination types come with #278. Declared
 * ahead the same way (TERMINATED_REQUESTED_QUEUE), same DLX.
 *
 * `notification.user_kyc_result_requested` tells a user the KYC decision on
 * their organisation; its one address is `payload.recipientEmail`. It rides
 * user-service's exchange, agricontract.user, in a row of its own. Unlike
 * contract-service, user-service declares no queue ahead of us, so this
 * declaration is the only one: until notification-service has started once,
 * user-service's publish is returned unroutable, and its outbox poller stops
 * at that row and retries it on every poll, holding back the rows after it.
 */
export const COMMAND_ROUTING_KEYS = [
  'notification.contract_anchor_requested',
  'notification.activation_cancel_requested',
  'notification.contract_activation_failed_requested',
  'notification.contract_terminated_requested',
  'notification.user_kyc_result_requested',
] as const;

export type CommandRoutingKey = (typeof COMMAND_ROUTING_KEYS)[number];

/** The flat Phase 1 events (D5), everything TOPOLOGY holds that is not a command. */
export type LegacyRoutingKey = Exclude<RoutingKey, CommandRoutingKey>;

export const isCommandRoutingKey = (
  key: RoutingKey,
): key is CommandRoutingKey =>
  (COMMAND_ROUTING_KEYS as readonly string[]).includes(key);

/** Routing keys of the flat Phase 1 events, in TOPOLOGY order. */
export const legacyRoutingKeys = (): LegacyRoutingKey[] =>
  TOPOLOGY.flatMap((row) => [...row.routingKeys]).filter(
    (key): key is LegacyRoutingKey => !isCommandRoutingKey(key),
  );

export const queueName = (routingKey: RoutingKey): string =>
  `notification-svc.${routingKey}`;

export const dlqName = (routingKey: RoutingKey): string =>
  `${queueName(routingKey)}.dlq`;

const routingKeys = (): RoutingKey[] =>
  TOPOLOGY.flatMap((row) => [...row.routingKeys]);

export const queueNames = (): string[] => routingKeys().map(queueName);

export const dlqNames = (): string[] => routingKeys().map(dlqName);

/**
 * Queues of the Phase 1 contract events this service no longer consumes
 * (#398), each with its DLQ. contract-service stopped publishing
 * contract.delivered, contract.cancelled and contract.disputed (V5), and
 * publishes contract.signed as a Phase 2 golden-flow event (D38) that the flat
 * Phase 1 parser rejected, so every signature dead-lettered one message here.
 * The signed-contract email comes from notification.contract_anchor_requested.
 *
 * Leaving TOPOLOGY does not remove a queue from a broker that already has it:
 * it stays bound and keeps filling with nobody consuming it. So
 * declareTopology deletes these on every start. Deleting a queue removes its
 * bindings, and RabbitMQ answers delete-ok for a queue that does not exist, so
 * it is a no-op once done. contract.signed still has other queues
 * (escrow-svc, audit-svc), so contract-service's mandatory publish still
 * routes.
 */
export const RETIRED_QUEUES = [
  'notification-svc.contract.signed',
  'notification-svc.contract.delivered',
  'notification-svc.contract.cancelled',
  'notification-svc.contract.disputed',
].flatMap((queue) => [queue, `${queue}.dlq`]);

export type TopologyChannel = Pick<
  Channel,
  'assertExchange' | 'assertQueue' | 'bindQueue' | 'deleteQueue'
>;

/** What declareTopology reports; a logger in production. */
export interface TopologyLog {
  warn(message: string): void;
}

/**
 * Declares exchanges, queues, DLQs and bindings exactly as the Java service
 * does, then deletes RETIRED_QUEUES.
 */
export async function declareTopology(
  channel: TopologyChannel,
  log?: TopologyLog,
): Promise<void> {
  await declareCurrent(channel);
  await retire(channel, log);
}

/**
 * Deletes each retired queue, its messages included: they are Phase 2
 * contract.signed events the flat parser could never read, or Phase 1 events
 * nobody publishes any more. Not `ifEmpty`: a non-empty queue would make
 * RabbitMQ close the channel, and the topology step would then fail on every
 * reconnect. The count dropped is logged instead.
 */
async function retire(
  channel: TopologyChannel,
  log: TopologyLog | undefined,
): Promise<void> {
  for (const queue of RETIRED_QUEUES) {
    const { messageCount } = await channel.deleteQueue(queue);
    if (messageCount > 0) {
      log?.warn(
        `Deleted retired queue ${queue} with ${messageCount} message(s) (#398)`,
      );
    }
  }
}

async function declareCurrent(channel: TopologyChannel): Promise<void> {
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
