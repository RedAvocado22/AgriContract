import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { AmqpChannelLike, AmqpMessageLike } from '@agricontract/eventing';
import { IngestEvent } from '../src/application/usecase/ingestEvent.js';
import type { ProjectionHandler } from '../src/application/port.js';
import { gracefulShutdown, type ShutdownSteps } from '../src/infrastructure/lifecycle.js';
import { startIngestion } from '../src/infrastructure/messaging/consumers.js';
import { CONTRACTS_EXCHANGE, declareTopology, queueName } from '../src/infrastructure/messaging/topology.js';
import { startAnalytics } from '../src/infrastructure/runtime.js';
import {
  FakeBroker,
  InMemoryDb,
  InMemoryIngestedEventStore,
  event,
  publishEvent,
  recordingLogger,
  silentLogger,
  type FakeTx,
} from './fakes.js';

function deferred() {
  let release!: () => void;
  const promise = new Promise<void>((r) => (release = r));
  return { promise, release };
}

/** Steps that record the order they run in. */
function recordingSteps(order: string[], overrides: Partial<ShutdownSteps> = {}): ShutdownSteps {
  return {
    markNotReady: () => void order.push('not-ready'),
    stopMonitor: () => void order.push('stop-monitor'),
    stopConsuming: async () => void order.push('stop-consuming'),
    stopHttp: async () => void order.push('stop-http'),
    awaitIdle: async () => {
      order.push('await-idle');
      return true;
    },
    closeDatabase: async () => void order.push('close-db'),
    closeBroker: async () => void order.push('close-broker'),
    ...overrides,
  };
}

describe('gracefulShutdown', () => {
  it('runs the steps in order: not ready, stop intake, drain, close MySQL, close RabbitMQ', async () => {
    const order: string[] = [];
    const result = await gracefulShutdown(recordingSteps(order), { logger: silentLogger, timeoutMs: 100 });
    assert.equal(result, 'CLEAN');
    assert.deepEqual(order, ['not-ready', 'stop-monitor', 'stop-consuming', 'stop-http', 'await-idle', 'close-db', 'close-broker']);
  });

  it('a failing step is logged and every later step still runs', async () => {
    const order: string[] = [];
    const logger = recordingLogger();
    const result = await gracefulShutdown(
      recordingSteps(order, {
        stopConsuming: async () => {
          throw new Error('channel closed');
        },
      }),
      { logger, timeoutMs: 100 },
    );
    assert.equal(result, 'CLEAN');
    assert.ok(order.includes('close-db') && order.includes('close-broker'));
    assert.ok(logger.entries.some((e) => e.msg === 'Shutdown step failed: stop consuming'));
  });

  it('a stuck close step is abandoned after stepTimeoutMs instead of hanging the process', async () => {
    const order: string[] = [];
    const never = new Promise<void>(() => {});
    await gracefulShutdown(recordingSteps(order, { stopHttp: () => never }), {
      logger: silentLogger,
      timeoutMs: 100,
      stepTimeoutMs: 20,
    });
    assert.ok(order.includes('close-broker'));
  });
});

describe('shutdown with an active handler (real wiring)', () => {
  function wiring(blocker: Promise<void>) {
    const broker = new FakeBroker(20);
    const db = new InMemoryDb();
    const log: string[] = [];
    const blocking: ProjectionHandler<FakeTx> = {
      eventType: 'contract.signed',
      async apply() {
        log.push('handler-started');
        await blocker;
        log.push('handler-finished');
      },
    };
    const start = () =>
      startAnalytics({
        consumeChannel: broker,
        monitorChannel: broker,
        transactions: db,
        processedEvents: db,
        store: new InMemoryIngestedEventStore(),
        projections: [blocking],
        logger: recordingLogger(),
        dlqMonitorIntervalMs: 60_000,
        sleep: async () => {},
      });
    return { broker, db, log, start };
  }

  const steps = (runtime: Awaited<ReturnType<ReturnType<typeof wiring>['start']>>, log: string[]): ShutdownSteps => ({
    markNotReady: () => void log.push('not-ready'),
    stopMonitor: () => runtime.stopMonitor(),
    stopConsuming: () => runtime.stopConsuming().then(() => void log.push('consuming-stopped')),
    stopHttp: async () => void log.push('http-stopped'),
    awaitIdle: (ms) => runtime.awaitIdle(ms),
    closeDatabase: async () => void log.push('db-closed'),
    closeBroker: async () => void log.push('broker-closed'),
  });

  it('waits for the running handler, acks its message, and only then closes the database', async () => {
    const gate = deferred();
    const { broker, db, log, start } = wiring(gate.promise);
    const runtime = await start();
    publishEvent(broker, CONTRACTS_EXCHANGE, event('contract.signed', 'c-1'));
    await new Promise((r) => setTimeout(r, 10));
    assert.equal(runtime.inFlightCount(), 1, 'the handler is running');

    const shutdown = gracefulShutdown(steps(runtime, log), { logger: silentLogger, timeoutMs: 2000 });
    await new Promise((r) => setTimeout(r, 30));
    assert.ok(!log.includes('db-closed'), 'database must stay open while the handler runs');
    assert.ok(log.includes('consuming-stopped'), 'no new deliveries');

    gate.release();
    assert.equal(await shutdown, 'CLEAN');
    assert.deepEqual(log, ['handler-started', 'not-ready', 'consuming-stopped', 'http-stopped', 'handler-finished', 'db-closed', 'broker-closed']);
    assert.equal(db.committed.ingested.length, 1, 'the in-flight event was committed');
    assert.equal(broker.unackedCount(), 0, 'and acked before the connection closed');
    assert.equal(broker.depth(queueName('contract.signed')), 0);
  });

  it('timeout path: a handler that never finishes does not block shutdown; resources are still closed', async () => {
    const gate = deferred();
    const { broker, log, start } = wiring(gate.promise);
    const runtime = await start();
    publishEvent(broker, CONTRACTS_EXCHANGE, event('contract.signed', 'c-1'));
    await new Promise((r) => setTimeout(r, 10));

    const logger = recordingLogger();
    const result = await gracefulShutdown(steps(runtime, log), { logger, timeoutMs: 40 });
    assert.equal(result, 'FORCED');
    assert.ok(log.includes('db-closed') && log.includes('broker-closed'));
    assert.ok(logger.entries.some((e) => e.msg.startsWith('In-flight deliveries did not finish')));
    assert.equal(broker.unackedCount(), 1, 'left unacked: the broker redelivers it and dedupe makes that safe');
    gate.release();
  });

  it('messages still waiting in the queue after shutdown are processed by the next instance', async () => {
    const { broker, db, start } = wiring(Promise.resolve());
    const runtime = await start();
    await runtime.stopConsuming();
    for (let i = 0; i < 5; i++) publishEvent(broker, CONTRACTS_EXCHANGE, event('contract.signed', `c-${i}`));
    assert.equal(broker.depth(queueName('contract.signed')), 5);
    await start();
    await broker.drain();
    assert.equal(db.committed.ingested.length, 5);
  });
});

describe('deliveries that arrive after shutdown began', () => {
  it('are handed back to the broker (requeue) without being handled', async () => {
    const callbacks: Array<(m: AmqpMessageLike | null) => void> = [];
    const nacks: Array<{ requeue: boolean | undefined }> = [];
    const channel: AmqpChannelLike = {
      async consume(_queue, onMessage) {
        callbacks.push(onMessage);
        return { consumerTag: `t${callbacks.length}` };
      },
      ack() {
        throw new Error('must not ack');
      },
      nack(_m, _all, requeue) {
        nacks.push({ requeue });
      },
      async cancel() {
        return {};
      },
    };
    const store = new InMemoryIngestedEventStore();
    const db = new InMemoryDb();
    const handle = await startIngestion({
      channel,
      transactions: db,
      processedEvents: db,
      ingest: new IngestEvent(store),
      logger: silentLogger,
    });
    await handle.stopConsuming();
    const env = event('contract.signed', 'c-1');
    callbacks[0]!({ content: Buffer.from(JSON.stringify(env)), fields: { routingKey: 'contract.signed' } });
    assert.deepEqual(nacks, [{ requeue: true }]);
    assert.equal(store.calls, 0);
    assert.equal(handle.inFlight.count, 0);
  });
});
