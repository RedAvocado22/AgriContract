import {
  Injectable,
  Logger,
  OnApplicationShutdown,
  OnModuleInit,
} from '@nestjs/common';
import {
  consumeEvents,
  type DeliveryOutcome,
  type MessageConsumer,
} from '@agricontract/eventing';
import { connect, type Channel } from 'amqplib';
import { eventingLogger } from './eventing-logger';

/*
 * M0 spike only (docs/planning/notification-service-nestjs-migration.md §6):
 * proves @agricontract/eventing (ESM) consumes from RabbitMQ inside a CommonJS
 * Nest app. Replaced by the real topology and consumers in M1/M2.
 */

export const M0_SPIKE_QUEUE = 'notification-svc.m0-spike';

/** Logs the raw body and reports PROCESSED without an envelope (the D6 case). */
export class M0SpikeMessageConsumer implements MessageConsumer {
  readonly consumerName = 'notification.m0-spike';

  constructor(private readonly logger: Logger) {}

  handleMessage(body: Buffer): Promise<DeliveryOutcome> {
    this.logger.log(`Received on ${M0_SPIKE_QUEUE}: ${body.toString('utf8')}`);
    return Promise.resolve({ status: 'PROCESSED' });
  }
}

@Injectable()
export class M0SpikeConsumer implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(M0SpikeConsumer.name);
  private connection?: Awaited<ReturnType<typeof connect>>;
  private channel?: Channel;
  private consumerTag?: string;

  async onModuleInit(): Promise<void> {
    const env = process.env;
    this.connection = await connect({
      hostname: env.RABBITMQ_HOST ?? 'localhost',
      port: Number(env.RABBITMQ_PORT ?? 5672),
      username: env.RABBITMQ_USERNAME ?? 'guest',
      password: env.RABBITMQ_PASSWORD ?? 'guest',
    });
    this.channel = await this.connection.createChannel();
    await this.channel.assertQueue(M0_SPIKE_QUEUE, {
      durable: false,
      autoDelete: true,
    });
    await this.channel.prefetch(10);
    this.consumerTag = await consumeEvents({
      channel: this.channel,
      queue: M0_SPIKE_QUEUE,
      consumer: new M0SpikeMessageConsumer(this.logger),
      logger: eventingLogger(this.logger),
    });
    this.logger.log(`Consuming ${M0_SPIKE_QUEUE}`);
  }

  async onApplicationShutdown(): Promise<void> {
    if (this.channel && this.consumerTag) {
      await this.channel.cancel(this.consumerTag);
    }
    await this.channel?.close();
    await this.connection?.close();
  }
}
