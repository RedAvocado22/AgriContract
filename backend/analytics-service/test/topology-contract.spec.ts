import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import yaml from 'js-yaml';
import { consumerName } from '../src/application/usecase/ingestEvent';
import { loadConfig } from '../src/infrastructure/config';
import { migrationFiles } from '../src/infrastructure/persistence/migrate';
import { PROJECTIONS } from '../src/infrastructure/persistence/projections';
import {
  ANALYTICS_DLX,
  CONTRACTS_EXCHANGE,
  SUBSCRIPTIONS,
  deadLetterQueueName,
  declareTopology,
  queueArguments,
  queueName,
} from '../src/infrastructure/messaging/topology';
import { FakeBroker } from './fakes';
import { deepEqual } from './deepEqual';

const here = __dirname;
const repoRoot = resolve(here, '../../..');

describe('RabbitMQ topology', () => {
  it('declares the producer exchange exactly like the Java services (topic, durable, not auto-delete)', async () => {
    const broker = new FakeBroker();
    await declareTopology(broker);
    deepEqual(broker.exchanges.get(CONTRACTS_EXCHANGE), { type: 'topic', options: { durable: true, autoDelete: false } });
    assert.equal(broker.exchanges.get(ANALYTICS_DLX)?.type, 'direct');
  });

  it('every subscription has a durable queue + DLQ bound by its eventType', async () => {
    const broker = new FakeBroker();
    await declareTopology(broker);
    for (const { eventType, exchange } of SUBSCRIPTIONS) {
      const q = broker.queues.get(queueName(eventType));
      assert.equal(q?.options.durable, true, eventType);
      assert.equal(q?.options.arguments?.['x-dead-letter-exchange'], ANALYTICS_DLX);
      assert.equal(broker.queues.get(deadLetterQueueName(eventType))?.options.durable, true);
      assert.ok(broker.bindings.some((b) => b.queue === queueName(eventType) && b.exchange === exchange && b.key === eventType));
      assert.ok(broker.bindings.some((b) => b.queue === deadLetterQueueName(eventType) && b.exchange === ANALYTICS_DLX && b.key === eventType));
    }
  });

  it('queues cannot push back on producers: no length limit, overflow policy, TTL or expiry', () => {
    const args = queueArguments();
    for (const forbidden of ['x-max-length', 'x-max-length-bytes', 'x-overflow', 'x-message-ttl', 'x-expires']) {
      assert.equal(forbidden in args, false, forbidden);
    }
  });

  it('declaring twice is a no-op (safe on every restart)', async () => {
    const broker = new FakeBroker();
    await declareTopology(broker);
    const before = broker.bindings.length;
    await declareTopology(broker);
    assert.equal(broker.bindings.length, before);
  });

  it('queue names follow the Java "<service>-svc.<routingKey>" convention', () => {
    assert.equal(queueName('contract.signed'), 'analytics-svc.contract.signed');
    assert.equal(deadLetterQueueName('contract.signed'), 'analytics-svc.contract.signed.dlq');
    assert.equal(consumerName('contract.signed'), 'analytics.contract.signed');
  });
});

describe('frozen event contract', () => {
  const catalog = yaml.load(readFileSync(join(repoRoot, 'contracts/events/golden-flow-events.yaml'), 'utf8')) as {
    $defs: Record<string, unknown>;
  };
  const canonical = new Set<string>();
  JSON.stringify(catalog.$defs, (key, value) => {
    if (key === 'eventType' && value && typeof value === 'object' && typeof (value as { const?: unknown }).const === 'string') {
      canonical.add((value as { const: string }).const);
    }
    return value;
  });

  it('every subscribed event type is a canonical event in golden-flow-events.yaml', () => {
    assert.ok(canonical.size > 20, 'catalog parsed');
    for (const { eventType } of SUBSCRIPTIONS) assert.ok(canonical.has(eventType), `${eventType} is not canonical`);
  });

  it('subscriptions cover the analytics consumer rows of the integration map', () => {
    const map = readFileSync(join(repoRoot, 'docs/contracts/integration-map.md'), 'utf8');
    // Flow rows: | step | owner | event(s) | consumers | rule |; keep rows whose consumer list names analytics.
    const rows = map
      .split('\n')
      .filter((l) => l.startsWith('|') && (l.split('|')[4] ?? '').split(',').some((c) => c.trim() === 'analytics'));
    const mentioned = rows.flatMap((r) => [...(r.split('|')[3] ?? '').matchAll(/`([a-z]+\.[a-z_]+)`/g)].map((m) => m[1]!));
    assert.ok(mentioned.length > 0, 'integration map rows found');
    for (const eventType of mentioned) {
      assert.ok(SUBSCRIPTIONS.some((s) => s.eventType === eventType), `${eventType} consumed by analytics per integration map`);
    }
  });

  it('no projection is registered yet (facts arrive in #133/#148); events are still ingested and stored', () => {
    assert.equal(PROJECTIONS.length, 0);
  });
});

describe('analytics is not on the transaction critical path (Matrix 23)', () => {
  const ANALYTICS_REFERENCE = /analytics-service|ANALYTICS_SERVICE_URL|\/api\/v1\/analytics|:8093\b/i;

  function files(dir: string): string[] {
    const out: string[] = [];
    for (const name of readdirSync(dir)) {
      if (['node_modules', 'target', 'dist'].includes(name)) continue;
      const p = join(dir, name);
      if (statSync(p).isDirectory()) out.push(...files(p));
      else if (/\.(java|ts|js|yml|yaml|properties)$/.test(name)) out.push(p);
    }
    return out;
  }

  for (const service of ['contract-service', 'escrow-service']) {
    it(`${service} has no synchronous client, URL or route to analytics-service`, () => {
      const offenders = files(join(repoRoot, 'backend', service)).filter((f) => ANALYTICS_REFERENCE.test(readFileSync(f, 'utf8')));
      deepEqual(offenders.map((f) => f.slice(repoRoot.length + 1)), []);
    });
  }

  it('analytics-service makes no outbound HTTP calls and publishes nothing back to core services', () => {
    for (const f of files(resolve(here, '../src'))) {
      const src = readFileSync(f, 'utf8');
      assert.doesNotMatch(src, /\bfetch\(|http\.request|https\.request|\.publish\(|sendToQueue\(/, f);
    }
  });
});

describe('persistence and config', () => {
  const strip = (file: string) => readFileSync(resolve(here, '../migrations', file), 'utf8').replace(/--.*$/gm, '');

  it('V1 is additive and creates the idempotency log used by the dedupe store', () => {
    const statements = strip('V1__create_analytics_idempotency_log.sql');
    assert.match(statements, /CREATE TABLE IF NOT EXISTS analytics_idempotency_log/);
    assert.match(statements, /PRIMARY KEY \(consumer_name, event_id\)/);
    assert.doesNotMatch(statements, /\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE)\b/i);
    assert.match(readFileSync(resolve(here, '../src/infrastructure/nest/analytics.lifecycle.ts'), 'utf8'), /table: 'analytics_idempotency_log'/);
  });

  it('V2 is additive and creates the landing table written by the ingest use case', () => {
    const statements = strip('V2__create_analytics_ingested_event.sql');
    assert.match(statements, /CREATE TABLE IF NOT EXISTS analytics_ingested_event/);
    assert.match(statements, /PRIMARY KEY \(event_id\)/);
    assert.match(statements, /payload\s+JSON\s+NOT NULL/);
    assert.doesNotMatch(statements, /\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE)\b/i);
    assert.doesNotMatch(statements, /email/i, 'no contact PII column');
  });

  it('migration runner finds both migrations in order', () => {
    deepEqual(migrationFiles(resolve(here, '../migrations')), [
      'V1__create_analytics_idempotency_log.sql',
      'V2__create_analytics_ingested_event.sql',
    ]);
  });

  it('defaults: port 8093, analytics_db, bounded prefetch, monitor and shutdown timing, RabbitMQ from the shared env names', () => {
    const c = loadConfig({});
    assert.equal(c.port, 8093);
    assert.equal(c.db.database, 'analytics_db');
    assert.equal(c.prefetch, 20);
    assert.equal(c.dlqMonitorIntervalMs, 30_000);
    assert.equal(c.shutdownTimeoutMs, 25_000);
    assert.equal(c.amqpUrl, 'amqp://guest:guest@localhost:5672');
    assert.equal(loadConfig({ RABBITMQ_HOST: 'rabbitmq', RABBITMQ_PASSWORD: 'p@ss' }).amqpUrl, 'amqp://guest:p%40ss@rabbitmq:5672');
    assert.equal(loadConfig({ DLQ_MONITOR_INTERVAL_MS: '5000', SHUTDOWN_TIMEOUT_MS: '9000' }).shutdownTimeoutMs, 9000);
  });
});

describe('NestJS wiring', () => {
  const read = (f: string) => readFileSync(resolve(here, '../src', f), 'utf8');
  const lifecycle = read('infrastructure/nest/analytics.lifecycle.ts');

  it('starts the shared production wiring and registers the projections list', () => {
    assert.match(lifecycle, /startAnalytics\(/);
    assert.match(lifecycle, /projections: PROJECTIONS/);
    assert.match(lifecycle, /new MysqlIngestedEventStore\(\)/);
  });

  it('starts MySQL and RabbitMQ in the background so /health listens first', () => {
    assert.match(lifecycle, /onApplicationBootstrap\(\): void/);
    assert.doesNotMatch(lifecycle, /await this\.start\(\)/);
  });

  it('runs the graceful shutdown on SIGTERM and SIGINT and ignores the expected connection close', () => {
    assert.match(read('main.ts'), /enableShutdownHooks\(\['SIGTERM', 'SIGINT'\]\)/);
    assert.match(lifecycle, /beforeApplicationShutdown\(/);
    assert.match(lifecycle, /gracefulShutdown\(/);
    assert.match(lifecycle, /if \(this\.shuttingDown\) return;/);
  });

  it('keeps the handles shutdown needs: pool, connection and channels', () => {
    for (const handle of ['this.pool = pool', 'this.connection = conn', 'this.channels = [consumeChannel, monitorChannel]']) {
      assert.ok(lifecycle.includes(handle), handle);
    }
  });

  it('Nest stays in infrastructure: modules, controller and lifecycle; the main.ts only bootstraps', () => {
    assert.match(read('infrastructure/nest/app.module.ts'), /@Module\(/);
    assert.match(read('infrastructure/http/health.controller.ts'), /@Controller\('health'\)/);
    assert.doesNotMatch(read('main.ts'), /amqp|mysql/);
  });
});
