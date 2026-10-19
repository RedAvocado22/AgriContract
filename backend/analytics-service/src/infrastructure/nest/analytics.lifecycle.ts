import { Inject, Injectable, type BeforeApplicationShutdown, type OnApplicationBootstrap } from '@nestjs/common';
import amqp from 'amqplib';
import mysql from 'mysql2/promise';
import {
  MysqlProcessedEventStore,
  MysqlTransactionRunner,
  type AmqpChannelLike,
  type Logger,
} from '@agricontract/eventing';
import type { Config } from '../config.js';
import { gracefulShutdown } from '../lifecycle.js';
import type { TopologyChannel } from '../messaging/topology.js';
import { MysqlIngestedEventStore } from '../persistence/ingestedEventStore.js';
import { PROJECTIONS } from '../persistence/projections.js';
import { startAnalytics, type AnalyticsRuntime } from '../runtime.js';
import { CONFIG, LOGGER } from './tokens.js';

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const message = (err: unknown) => (err instanceof Error ? err.message : String(err));

class StartupAborted extends Error {}

/**
 * Owns the MySQL pool, the RabbitMQ connection and the ingestion runtime. Nest creates it, calls
 * onApplicationBootstrap (which starts the work in the background, so the HTTP server is already
 * listening and /health answers 503 STARTING while dependencies are down) and, on SIGTERM/SIGINT,
 * beforeApplicationShutdown (the graceful shutdown of rules §4B; Nest then closes the HTTP server).
 */
@Injectable()
export class AnalyticsLifecycle implements OnApplicationBootstrap, BeforeApplicationShutdown {
  private ready = false;
  private shuttingDown = false;
  private pool: ReturnType<typeof mysql.createPool> | undefined;
  private connection: Awaited<ReturnType<typeof amqp.connect>> | undefined;
  private channels: Array<{ close(): Promise<void> }> = [];
  private runtime: AnalyticsRuntime | undefined;
  private starting: Promise<void> = Promise.resolve();

  constructor(
    @Inject(CONFIG) private readonly config: Config,
    @Inject(LOGGER) private readonly logger: Logger,
  ) {}

  isReady(): boolean {
    return this.ready;
  }

  onApplicationBootstrap(): void {
    // Not awaited on purpose: Nest must go on and start listening while dependencies are still down.
    this.starting = this.start().catch((err: unknown) => {
      if (err instanceof StartupAborted) return;
      this.logger.error('analytics-service failed to start', { error: message(err) });
      process.exit(1);
    });
  }

  /** Analytics is non-critical: if a dependency is down at startup, keep retrying instead of crashing. */
  private async withRetry<T>(what: string, fn: () => Promise<T>): Promise<T> {
    for (let attempt = 1; ; attempt += 1) {
      if (this.shuttingDown) throw new StartupAborted();
      try {
        return await fn();
      } catch (err) {
        const wait = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5));
        this.logger.warn(`${what} unavailable, retrying`, { attempt, waitMs: wait, error: message(err) });
        await sleep(wait);
      }
    }
  }

  private async start(): Promise<void> {
    const { config, logger } = this;
    const pool = mysql.createPool({ ...config.db, connectionLimit: 10, dateStrings: true, timezone: 'Z' });
    this.pool = pool;
    await this.withRetry('MySQL', () => pool.query('SELECT 1'));

    const conn = await this.withRetry('RabbitMQ', () => amqp.connect(config.amqpUrl));
    this.connection = conn;
    conn.on('close', () => {
      if (this.shuttingDown) return;
      // Messages not yet acked stay in the durable queues and are redelivered; dedupe handles repeats.
      logger.error('RabbitMQ connection closed, exiting so the orchestrator restarts the service');
      process.exit(1);
    });
    conn.on('error', (err: Error) => logger.error('RabbitMQ connection error', { error: err.message }));

    const consumeChannel = await conn.createChannel();
    const monitorChannel = await conn.createChannel();
    this.channels = [consumeChannel, monitorChannel];
    for (const channel of [consumeChannel, monitorChannel]) {
      channel.on('error', (err: Error) => logger.error('RabbitMQ channel error', { error: err.message }));
    }
    consumeChannel.on('close', () => {
      if (this.shuttingDown) return;
      logger.error('RabbitMQ consume channel closed, exiting so the orchestrator restarts the service');
      process.exit(1);
    });
    await consumeChannel.prefetch(config.prefetch);
    if (this.shuttingDown) throw new StartupAborted();

    this.runtime = await startAnalytics({
      consumeChannel: consumeChannel as unknown as AmqpChannelLike & TopologyChannel,
      monitorChannel,
      transactions: new MysqlTransactionRunner(pool),
      processedEvents: new MysqlProcessedEventStore({ table: 'analytics_idempotency_log' }),
      store: new MysqlIngestedEventStore(),
      projections: PROJECTIONS,
      logger,
      dlqMonitorIntervalMs: config.dlqMonitorIntervalMs,
    });
    this.ready = !this.shuttingDown;
    logger.info('analytics-service ready', { projections: PROJECTIONS.map((p) => p.eventType) });
  }

  async beforeApplicationShutdown(signal?: string): Promise<void> {
    if (this.shuttingDown) return;
    this.shuttingDown = true;
    this.logger.info('Signal received, shutting down', { signal });
    const result = await gracefulShutdown(
      {
        markNotReady: () => {
          this.ready = false;
        },
        stopMonitor: () => this.runtime?.stopMonitor(),
        // Nest closes the HTTP server right after this hook, once consumption has stopped.
        stopHttp: async () => {},
        stopConsuming: async () => {
          await this.runtime?.stopConsuming();
        },
        awaitIdle: async (ms) => (this.runtime ? this.runtime.awaitIdle(ms) : true),
        closeDatabase: async () => {
          await this.pool?.end();
        },
        closeBroker: async () => {
          for (const channel of this.channels) await channel.close().catch(() => {});
          await this.connection?.close();
        },
      },
      { logger: this.logger, timeoutMs: this.config.shutdownTimeoutMs },
    );
    await this.starting;
    if (result !== 'CLEAN') process.exitCode = 1;
  }
}
