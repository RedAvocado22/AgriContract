import {
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
  } as unknown as TopologyChannel;
  return { channel, exchanges, queues, bindings };
}

const EXPECTED_QUEUES = [
  'notification-svc.contract.signed',
  'notification-svc.contract.delivered',
  'notification-svc.contract.cancelled',
  'notification-svc.contract.disputed',
  'notification-svc.escrow.locked',
  'notification-svc.escrow.released',
  'notification-svc.escrow.penalized',
  'notification-svc.category.approved',
  'notification-svc.category.rejected',
];

describe('topology (must match the Java RabbitMQConfig)', () => {
  it('names the 9 queues and 9 DLQs', () => {
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
      ['agricontract.escrow', 'topic', durable],
      ['notification-svc.escrow.dlx', 'direct', durable],
      ['agricontract.events', 'topic', durable],
      ['notification-svc.events.dlx', 'direct', durable],
    ]);
  });

  it('declares 18 queues: main queues with only x-dead-letter-exchange, DLQs without arguments', async () => {
    const { channel, queues } = recordingChannel();
    await declareTopology(channel);

    expect(queues).toHaveLength(18);
    const dlxOf = (q: string) =>
      q.includes('.contract.')
        ? 'notification-svc.contracts.dlx'
        : q.includes('.escrow.')
          ? 'notification-svc.escrow.dlx'
          : 'notification-svc.events.dlx';

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

    expect(bindings).toHaveLength(18);
    expect(bindings).toContainEqual([
      'notification-svc.contract.signed',
      'agricontract.contracts',
      'contract.signed',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.contract.signed.dlq',
      'notification-svc.contracts.dlx',
      'contract.signed',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.escrow.penalized',
      'agricontract.escrow',
      'escrow.penalized',
    ]);
    expect(bindings).toContainEqual([
      'notification-svc.escrow.penalized.dlq',
      'notification-svc.escrow.dlx',
      'escrow.penalized',
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
});
