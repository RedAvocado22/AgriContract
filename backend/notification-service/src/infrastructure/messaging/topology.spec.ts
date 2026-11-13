import {
  RETIRED_QUEUES,
  declareTopology,
  dlqNames,
  queueNames,
  type TopologyChannel,
} from './topology';

/** Records every declaration instead of talking to a broker. */
function recordingChannel() {
  const exchanges: unknown[][] = [];
  const queues: unknown[][] = [];
  const bindings: unknown[][] = [];
  const deleted: string[] = [];
  const channel = {
    assertExchange: (...args: unknown[]) => {
      exchanges.push(args);
      return Promise.resolve({ exchange: args[0] });
    },
    assertQueue: (...args: unknown[]) => {
      queues.push(args);
      return Promise.resolve({ queue: args[0] });
    },
    bindQueue: (...args: unknown[]) => {
      bindings.push(args);
      return Promise.resolve({});
    },
    deleteQueue: (queue: string) => {
      deleted.push(queue);
      return Promise.resolve({ messageCount: 0 });
    },
  } as unknown as TopologyChannel;
  return { channel, exchanges, queues, bindings, deleted };
}

const EXPECTED_QUEUES = [
  // Phase 2 commands, on the publisher's exchange.
  'notification-svc.notification.contract_anchor_requested',
  'notification-svc.notification.activation_cancel_requested',
  'notification-svc.notification.contract_activation_failed_requested',
  'notification-svc.notification.contract_terminated_requested',
  'notification-svc.category.approved',
  'notification-svc.category.rejected',
  'notification-svc.notification.user_kyc_result_requested',
  'notification-svc.notification.user_lock_changed_requested',
];

/** The exchange row a queue belongs to, from the routing key in its name. */
const dlxOf = (queue: string): string => {
  const key = queue.replace('notification-svc.', '').replace(/\.dlq$/, '');
  if (key.startsWith('category.')) return 'notification-svc.events.dlx';
  if (key.startsWith('notification.user_')) return 'notification-svc.user.dlx';
  return 'notification-svc.contracts.dlx';
};

describe('topology (must match the Java RabbitMQConfig)', () => {
  it('names the 8 queues and 8 DLQs', () => {
    expect(queueNames()).toEqual(EXPECTED_QUEUES);
    expect(dlqNames()).toEqual(EXPECTED_QUEUES.map((q) => `${q}.dlq`));
  });

  it('declares exactly 6 durable, non auto-delete exchanges', async () => {
    const { channel, exchanges } = recordingChannel();
    await declareTopology(channel);

    const durable = { durable: true, autoDelete: false };
    expect(exchanges).toEqual([
      ['agricontract.contracts', 'topic', durable],
      ['notification-svc.contracts.dlx', 'direct', durable],
      ['agricontract.events', 'topic', durable],
      ['notification-svc.events.dlx', 'direct', durable],
      // user-service declares agricontract.user with the same options
      // (RabbitConfiguration.userExchange: durable, not auto-delete).
      ['agricontract.user', 'topic', durable],
      ['notification-svc.user.dlx', 'direct', durable],
    ]);
  });

  it('declares 16 queues: main queues with only x-dead-letter-exchange, DLQs without arguments', async () => {
    const { channel, queues } = recordingChannel();
    await declareTopology(channel);

    expect(queues).toHaveLength(16);

    for (const q of EXPECTED_QUEUES) {
      // toEqual on the whole options object also fails on any extra option
      // (exclusive, autoDelete, messageTtl, deadLetterRoutingKey, maxLength...).
      expect(queues).toContainEqual([
        q,
        { durable: true, arguments: { 'x-dead-letter-exchange': dlxOf(q) } },
      ]);
      expect(queues).toContainEqual([`${q}.dlq`, { durable: true }]);
    }

    for (const [name, options] of queues as Array<
      [string, Record<string, unknown>]
    >) {
      if (name.endsWith('.dlq')) {
        expect(options).toEqual({ durable: true });
        expect(options).not.toHaveProperty('arguments');
      } else {
        expect(Object.keys(options.arguments as object)).toEqual([
          'x-dead-letter-exchange',
        ]);
      }
    }
  });

  it('binds each queue to its exchange and each DLQ to its DLX with the same routing key', async () => {
    const { channel, bindings } = recordingChannel();
    await declareTopology(channel);

    expect(bindings).toHaveLength(16);
    // user-service publishes the KYC command to its own exchange and declares
    // nothing for us, so this binding is the only route it has.
    expect(bindings).toContainEqual([
      'notification-svc.notification.user_kyc_result_requested',
      'agricontract.user',
      'notification.user_kyc_result_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.user_kyc_result_requested.dlq',
      'notification-svc.user.dlx',
      'notification.user_kyc_result_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.user_lock_changed_requested',
      'agricontract.user',
      'notification.user_lock_changed_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.user_lock_changed_requested.dlq',
      'notification-svc.user.dlx',
      'notification.user_lock_changed_requested',
    ]);
    // Exactly the queue, DLX and binding contract-service declares ahead of us
    // (ConsumerQueuesDeclaredAhead), so its declaration and ours are the same.
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_anchor_requested',
      'agricontract.contracts',
      'notification.contract_anchor_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_anchor_requested.dlq',
      'notification-svc.contracts.dlx',
      'notification.contract_anchor_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.activation_cancel_requested',
      'agricontract.contracts',
      'notification.activation_cancel_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.activation_cancel_requested.dlq',
      'notification-svc.contracts.dlx',
      'notification.activation_cancel_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_terminated_requested',
      'agricontract.contracts',
      'notification.contract_terminated_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_terminated_requested.dlq',
      'notification-svc.contracts.dlx',
      'notification.contract_terminated_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_activation_failed_requested',
      'agricontract.contracts',
      'notification.contract_activation_failed_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.notification.contract_activation_failed_requested.dlq',
      'notification-svc.contracts.dlx',
      'notification.contract_activation_failed_requested',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.category.rejected',
      'agricontract.events',
      'category.rejected',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.category.rejected.dlq',
      'notification-svc.events.dlx',
      'category.rejected',
    ]);
    for (const [queue, , key] of bindings as string[][]) {
      expect(queue.replace(/\.dlq$/, '')).toBe(`notification-svc.${key}`);
    }
  });

  /*
   * #398 (contract events) and #421 (escrow events): neither is consumed any
   * more, and nothing publishes them. A broker that already has their queues
   * keeps them bound and filling until they are deleted, so every start deletes
   * them, after the current topology.
   */
  describe('retired Phase 1 queues', () => {
    const RETIRED = [
      ...['signed', 'delivered', 'cancelled', 'disputed'].map(
        (event) => `notification-svc.contract.${event}`,
      ),
      ...['locked', 'released', 'penalized'].map(
        (event) => `notification-svc.escrow.${event}`,
      ),
    ].flatMap((queue) => [queue, `${queue}.dlq`]);

    it('are exactly the four contract and three escrow queues, with their DLQs', () => {
      expect([...RETIRED_QUEUES].sort()).toEqual([...RETIRED].sort());
    });

    it('are deleted on every declaration, after the current queues are declared', async () => {
      const order: string[] = [];
      const { channel, deleted } = recordingChannel();
      const traced = {
        ...channel,
        assertQueue: (...args: Parameters<TopologyChannel['assertQueue']>) => {
          order.push('assert');
          return channel.assertQueue(...args);
        },
        deleteQueue: (...args: Parameters<TopologyChannel['deleteQueue']>) => {
          order.push('delete');
          return channel.deleteQueue(...args);
        },
      };

      await declareTopology(traced);
      await declareTopology(traced);

      expect(deleted).toEqual([...RETIRED_QUEUES, ...RETIRED_QUEUES]);
      // Each declaration asserts every queue and its DLQ, then deletes.
      const asserted = queueNames().length * 2;
      const retired = asserted + RETIRED_QUEUES.length;
      expect(order.slice(0, asserted).every((step) => step === 'assert')).toBe(
        true,
      );
      expect(
        order.slice(asserted, retired).every((step) => step === 'delete'),
      ).toBe(true);
    });

    it('are never declared, bound, consumed or watched', async () => {
      const { channel, queues, bindings } = recordingChannel();
      await declareTopology(channel);

      const touched = [
        ...queues.map(([q]) => q),
        ...bindings.map(([q]) => q),
        ...queueNames(),
        ...dlqNames(),
      ];
      for (const queue of RETIRED) expect(touched).not.toContain(queue);
    });

    it('reports a deleted queue that still held messages, and only that one', async () => {
      const { channel } = recordingChannel();
      const warn = jest.fn();
      const counting = {
        ...channel,
        deleteQueue: (queue: string) =>
          Promise.resolve({
            messageCount:
              queue === 'notification-svc.contract.signed.dlq' ? 3 : 0,
          }),
      };

      await declareTopology(counting, { warn });

      expect(warn.mock.calls).toEqual([
        [
          'Deleted retired queue notification-svc.contract.signed.dlq with 3 message(s) (#398, #421)',
        ],
      ]);
    });
  });
});
