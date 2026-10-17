import {
  Inject,
  Injectable,
  Logger,
  type OnApplicationShutdown,
  type OnModuleDestroy,
  type OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel, ChannelModel, Options } from 'amqplib';
import type { AppConfig } from '../../config/env.validation';
import { describeError } from '../describe-error';
import { declareTopology } from './topology';

/** Injection token of amqplib's `connect`, replaced by a fake in tests. */
export const AMQP_CONNECT = Symbol('AMQP_CONNECT');
export type AmqpConnect = (
  url: Options.Connect,
  socketOptions?: Record<string, unknown>,
) => Promise<ChannelModel>;

export type ChannelReadyCallback = (channel: Channel) => Promise<void>;

/** Messages in flight per consumer channel (node-eventing README: always set prefetch). */
export const PREFETCH = 10;
const INITIAL_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;
/** Same as Spring AMQP's default heartbeat, so a dead broker is noticed. */
const HEARTBEAT_SECONDS = 60;
const CONNECT_TIMEOUT_MS = 10_000;

/**
 * One RabbitMQ connection and channel, kept alive the way Spring AMQP does:
 * startup never waits for the broker, failures are retried in the background
 * (1s, 2s, 4s ... capped at 30s), and every successful (re)connect sets the
 * prefetch, declares the topology and runs the `onChannelReady` callbacks
 * (M2 attaches the consumers there). M1 consumes nothing.
 */
@Injectable()
export class AmqpService
  implements OnModuleInit, OnModuleDestroy, OnApplicationShutdown
{
  private readonly logger = new Logger(AmqpService.name);
  private readonly readyCallbacks: ChannelReadyCallback[] = [];
  private connection?: ChannelModel;
  private channel?: Channel;
  private retryTimer?: NodeJS.Timeout;
  private failures = 0;
  private stopped = false;

  constructor(
    private readonly config: ConfigService<AppConfig, true>,
    @Inject(AMQP_CONNECT) private readonly connect: AmqpConnect,
  ) {}

  onModuleInit(): void {
    void this.establish();
  }

  /** True while a channel with the declared topology is open. */
  isHealthy(): boolean {
    return this.channel !== undefined;
  }

  getChannel(): Channel {
    if (!this.channel) throw new Error('RabbitMQ channel is not ready');
    return this.channel;
  }

  /** Runs `callback` on every channel that becomes ready, including the current one. */
  async onChannelReady(callback: ChannelReadyCallback): Promise<void> {
    this.readyCallbacks.push(callback);
    if (this.channel) await callback(this.channel);
  }

  /**
   * Shutdown, step 1: stop reconnecting but keep the channel open, so the
   * consumers can be cancelled and in-flight messages finish and get acked
   * (NotificationEventConsumer.beforeApplicationShutdown, step 2).
   */
  onModuleDestroy(): void {
    this.stopped = true;
    clearTimeout(this.retryTimer);
    this.retryTimer = undefined;
  }

  /** Shutdown, step 3: close the channel, then the connection. */
  async onApplicationShutdown(): Promise<void> {
    this.onModuleDestroy();
    const { channel, connection } = this;
    this.channel = undefined;
    this.connection = undefined;
    await this.closeQuietly(channel);
    await this.closeQuietly(connection);
  }

  private async establish(): Promise<void> {
    if (this.stopped) return;
    const rabbit = this.config.get('rabbitmq', { infer: true });
    const target = `${rabbit.host}:${rabbit.port}`;
    let connection: ChannelModel | undefined;
    let channel: Channel | undefined;
    let step: 'connect' | 'topology' = 'connect';

    try {
      connection = await this.connect(
        {
          protocol: 'amqp',
          hostname: rabbit.host,
          port: rabbit.port,
          username: rabbit.username,
          password: rabbit.password,
          heartbeat: HEARTBEAT_SECONDS,
        },
        { timeout: CONNECT_TIMEOUT_MS },
      );
      const conn = connection;
      conn.on('error', (err: Error) =>
        this.logger.warn(`RabbitMQ connection error: ${err.message}`),
      );
      conn.on('close', () => this.lost(conn, 'connection closed'));

      channel = await conn.createChannel();
      const ch = channel;
      ch.on('error', (err: Error) =>
        this.logger.warn(`RabbitMQ channel error: ${err.message}`),
      );
      ch.on('close', () => this.lost(conn, 'channel closed'));

      step = 'topology';
      await ch.prefetch(PREFETCH);
      await declareTopology(ch);
      for (const callback of this.readyCallbacks) await callback(ch);

      if (this.stopped) {
        await this.closeQuietly(ch);
        await this.closeQuietly(conn);
        return;
      }
      this.connection = conn;
      this.channel = ch;
      this.failures = 0;
      this.logger.log(`Connected to RabbitMQ at ${target}, topology declared`);
    } catch (err) {
      const reason = describeError(err);
      const delay = this.scheduleRetry();
      if (step === 'topology') {
        this.logger.error(
          `RabbitMQ channel setup failed at ${target} (topology or consumers): ${reason}; retrying in ${delay}ms`,
        );
      } else {
        this.logger.warn(
          `Cannot connect to RabbitMQ at ${target}: ${reason}; retrying in ${delay}ms`,
        );
      }
      await this.closeQuietly(channel);
      await this.closeQuietly(connection);
    }
  }

  /** Called on close of the live connection or channel; ignores ones being set up or torn down. */
  private lost(connection: ChannelModel, why: string): void {
    if (this.stopped || this.connection !== connection) return;
    const channel = this.channel;
    this.connection = undefined;
    this.channel = undefined;
    const delay = this.scheduleRetry();
    this.logger.warn(`RabbitMQ ${why}; reconnecting in ${delay}ms`);
    void this.closeQuietly(channel).then(() => this.closeQuietly(connection));
  }

  /** Schedules `establish` with exponential backoff and returns the delay used. */
  private scheduleRetry(): number {
    const delay = Math.min(
      INITIAL_BACKOFF_MS * 2 ** this.failures,
      MAX_BACKOFF_MS,
    );
    if (this.stopped || this.retryTimer) return delay;
    this.failures += 1;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      void this.establish();
    }, delay);
    return delay;
  }

  /** Closes a channel or connection, ignoring "already closed" and similar errors. */
  private async closeQuietly(target?: {
    close(): Promise<void>;
  }): Promise<void> {
    if (!target) return;
    try {
      await target.close();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      if (!/closed|closing/i.test(message)) {
        this.logger.debug(`Ignoring error while closing RabbitMQ: ${message}`);
      }
    }
  }
}
