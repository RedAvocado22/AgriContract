import type { Server } from 'node:http';
import amqp from 'amqplib';
import mysql from 'mysql2/promise';
import {
  MysqlProcessedEventStore,
  MysqlTransactionRunner,
  type AmqpChannelLike,
  type Logger,
} from '@agricontract/eventing';
import { loadConfig } from './infrastructure/config.js';
import { createHealthServer } from './infrastructure/http/health.js';
import { gracefulShutdown } from './infrastructure/lifecycle.js';
import type { TopologyChannel } from './infrastructure/messaging/topology.js';
import { MysqlIngestedEventStore } from './infrastructure/persistence/ingestedEventStore.js';
import { PROJECTIONS } from './infrastructure/persistence/projections.js';
import { startAnalytics, type AnalyticsRuntime } from './infrastructure/runtime.js';

const logger: Logger = {
  debug: () => {},
  info: (m, meta) => console.info(JSON.stringify({ level: 'info', msg: m, ...meta })),
  warn: (m, meta) => console.warn(JSON.stringify({ level: 'warn', msg: m, ...meta })),
  error: (m, meta) => console.error(JSON.stringify({ level: 'error', msg: m, ...meta })),
};

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const message = (err: unknown) => (err instanceof Error ? err.message : String(err));

/** Analytics is non-critical: if a dependency is down at startup, keep retrying instead of crashing producers' flow. */
async function withRetry<T>(what: string, fn: () => Promise<T>): Promise<T> {
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await fn();
    } catch (err) {
      const wait = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5));
      logger.warn(`${what} unavailable, retrying`, { attempt, waitMs: wait, error: message(err) });
      await sleep(wait);
    }
  }
}

/** Composition root: the only place that creates infrastructure resources and wires them together. */
async function main(): Promise<void> {
  const config = loadConfig();
  let ready = false;
  let shuttingDown = false;

  // Handles kept for graceful shutdown (rules §4B). They stay undefined until the resource exists.
  let server: Server | undefined;
  let pool: ReturnType<typeof mysql.createPool> | undefined;
  let connection: Awaited<ReturnType<typeof amqp.connect>> | undefined;
  let channels: Array<{ close(): Promise<void> }> = [];
  let runtime: AnalyticsRuntime | undefined;

  const shutdown = async (signal: string): Promise<void> => {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info('Signal received, shutting down', { signal });
    const result = await gracefulShutdown(
      {
        markNotReady: () => {
          ready = false;
        },
        stopMonitor: () => runtime?.stopMonitor(),
        stopHttp: () =>
          new Promise<void>((resolve) => {
            if (!server) return resolve();
            server.close(() => resolve());
            server.closeIdleConnections();
          }),
        stopConsuming: async () => {
          await runtime?.stopConsuming();
        },
        awaitIdle: async (ms) => (runtime ? runtime.awaitIdle(ms) : true),
        closeDatabase: async () => {
          await pool?.end();
        },
        closeBroker: async () => {
          for (const channel of channels) await channel.close().catch(() => {});
          await connection?.close();
        },
      },
      { logger, timeoutMs: config.shutdownTimeoutMs },
    );
    process.exit(result === 'CLEAN' ? 0 : 1);
  };
  process.once('SIGTERM', () => void shutdown('SIGTERM'));
  process.once('SIGINT', () => void shutdown('SIGINT'));

  server = createHealthServer(() => ready).listen(config.port, () =>
    logger.info('analytics-service health endpoint listening', { port: config.port }),
  );

  pool = mysql.createPool({ ...config.db, connectionLimit: 10, dateStrings: true, timezone: 'Z' });
  const startupPool = pool;
  await withRetry('MySQL', () => startupPool.query('SELECT 1'));

  const conn = await withRetry('RabbitMQ', () => amqp.connect(config.amqpUrl));
  connection = conn;
  conn.on('close', () => {
    if (shuttingDown) return;
    // Messages not yet acked stay in the durable queues and are redelivered; dedupe handles repeats.
    logger.error('RabbitMQ connection closed, exiting so the orchestrator restarts the service');
    process.exit(1);
  });
  conn.on('error', (err: Error) => logger.error('RabbitMQ connection error', { error: err.message }));

  const consumeChannel = await conn.createChannel();
  const monitorChannel = await conn.createChannel();
  channels = [consumeChannel, monitorChannel];
  for (const channel of channels as Array<typeof consumeChannel>) {
    channel.on('error', (err: Error) => logger.error('RabbitMQ channel error', { error: err.message }));
  }
  consumeChannel.on('close', () => {
    if (shuttingDown) return;
    logger.error('RabbitMQ consume channel closed, exiting so the orchestrator restarts the service');
    process.exit(1);
  });
  await consumeChannel.prefetch(config.prefetch);

  runtime = await startAnalytics({
    consumeChannel: consumeChannel as unknown as AmqpChannelLike & TopologyChannel,
    monitorChannel,
    transactions: new MysqlTransactionRunner(pool),
    processedEvents: new MysqlProcessedEventStore({ table: 'analytics_idempotency_log' }),
    store: new MysqlIngestedEventStore(),
    projections: PROJECTIONS,
    logger,
    dlqMonitorIntervalMs: config.dlqMonitorIntervalMs,
  });
  ready = !shuttingDown;
  logger.info('analytics-service ready', { projections: PROJECTIONS.map((p) => p.eventType) });
}

main().catch((err) => {
  logger.error('analytics-service failed to start', { error: message(err) });
  process.exit(1);
});
