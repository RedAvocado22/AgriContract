import { createServer } from 'node:http';
import amqp from 'amqplib';
import mysql from 'mysql2/promise';
import {
  MysqlProcessedEventStore,
  MysqlTransactionRunner,
  type AmqpChannelLike,
  type Logger,
} from '@agricontract/eventing';
import { loadConfig } from './config.js';
import { HANDLERS } from './handlers.js';
import { startIngestion } from './ingestion.js';
import { declareTopology } from './topology.js';

const logger: Logger = {
  debug: () => {},
  info: (m, meta) => console.info(JSON.stringify({ level: 'info', msg: m, ...meta })),
  warn: (m, meta) => console.warn(JSON.stringify({ level: 'warn', msg: m, ...meta })),
  error: (m, meta) => console.error(JSON.stringify({ level: 'error', msg: m, ...meta })),
};

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** Analytics is non-critical: if a dependency is down at startup, keep retrying instead of crashing producers' flow. */
async function withRetry<T>(what: string, fn: () => Promise<T>): Promise<T> {
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await fn();
    } catch (err) {
      const wait = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5));
      logger.warn(`${what} unavailable, retrying`, { attempt, waitMs: wait, error: err instanceof Error ? err.message : String(err) });
      await sleep(wait);
    }
  }
}

async function main(): Promise<void> {
  const config = loadConfig();
  let ready = false;

  createServer((req, res) => {
    const ok = req.url === '/health';
    res.writeHead(ok ? (ready ? 200 : 503) : 404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(ok ? { status: ready ? 'UP' : 'STARTING' } : { status: 'NOT_FOUND' }));
  }).listen(config.port, () => logger.info('analytics-service health endpoint listening', { port: config.port }));

  const pool = mysql.createPool({ ...config.db, connectionLimit: 10, dateStrings: true, timezone: 'Z' });
  await withRetry('MySQL', () => pool.query('SELECT 1'));

  const connection = await withRetry('RabbitMQ', () => amqp.connect(config.amqpUrl));
  connection.on('close', () => {
    // Messages not yet acked stay in the durable queues and are redelivered; dedupe handles repeats.
    logger.error('RabbitMQ connection closed, exiting so the orchestrator restarts the service');
    process.exit(1);
  });
  connection.on('error', (err: Error) => logger.error('RabbitMQ connection error', { error: err.message }));

  const channel = await connection.createChannel();
  await channel.prefetch(config.prefetch);
  await declareTopology(channel);

  await startIngestion({
    channel: channel as unknown as AmqpChannelLike,
    transactions: new MysqlTransactionRunner(pool),
    processedEvents: new MysqlProcessedEventStore({ table: 'analytics_idempotency_log' }),
    handlers: HANDLERS,
    logger,
  });
  ready = true;
  logger.info('analytics-service ready', { handlers: HANDLERS.map((h) => h.eventType) });
}

main().catch((err) => {
  logger.error('analytics-service failed to start', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
});
