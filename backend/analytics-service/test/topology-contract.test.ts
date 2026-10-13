import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { loadConfig } from '../src/config.js';
import { HANDLERS } from '../src/handlers.js';
import { consumerName } from '../src/ingestion.js';
import { migrationFiles } from '../src/migrate.js';
import {
  ANALYTICS_DLX,
  CONTRACTS_EXCHANGE,
  SUBSCRIPTIONS,
  deadLetterQueueName,
  declareTopology,
  queueArguments,
  queueName,
} from '../src/topology.js';
import { FakeBroker } from './fakes.js';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../..');

describe('RabbitMQ topology', () => {
  it('declares the producer exchange exactly like the Java services (topic, durable, not auto-delete)', async () => {
    const broker = new FakeBroker();
    await declareTopology(broker);
    assert.deepEqual(broker.exchanges.get(CONTRACTS_EXCHANGE), { type: 'topic', options: { durable: true, autoDelete: false } });
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

  it('this scaffold registers no projection handler yet (facts arrive in #133/#148)', () => {
    assert.equal(HANDLERS.length, 0);
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
      assert.deepEqual(offenders.map((f) => f.slice(repoRoot.length + 1)), []);
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
  const sql = readFileSync(resolve(here, '../migrations/V1__create_analytics_idempotency_log.sql'), 'utf8');
  const statements = sql.replace(/--.*$/gm, '');

  it('migration is additive and creates the idempotency log used by the dedupe store', () => {
    assert.match(statements, /CREATE TABLE IF NOT EXISTS analytics_idempotency_log/);
    assert.match(statements, /PRIMARY KEY \(consumer_name, event_id\)/);
    assert.doesNotMatch(statements, /\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE)\b/i);
    assert.match(readFileSync(resolve(here, '../src/main.ts'), 'utf8'), /table: 'analytics_idempotency_log'/);
  });

  it('migration runner finds the migration', () => {
    assert.deepEqual(migrationFiles(resolve(here, '../migrations')), ['V1__create_analytics_idempotency_log.sql']);
  });

  it('defaults: port 8093, analytics_db, bounded prefetch, RabbitMQ from the shared env names', () => {
    const c = loadConfig({});
    assert.equal(c.port, 8093);
    assert.equal(c.db.database, 'analytics_db');
    assert.equal(c.prefetch, 20);
    assert.equal(c.amqpUrl, 'amqp://guest:guest@localhost:5672');
    assert.equal(loadConfig({ RABBITMQ_HOST: 'rabbitmq', RABBITMQ_PASSWORD: 'p@ss' }).amqpUrl, 'amqp://guest:p%40ss@rabbitmq:5672');
  });
});
