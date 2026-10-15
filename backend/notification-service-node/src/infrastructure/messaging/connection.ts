import amqp from 'amqp-connection-manager';
import type { AmqpConnectionManager, ChannelWrapper } from 'amqp-connection-manager';
import type { AppConfig } from '../../config.js';

export function connectRabbitMq(config: AppConfig['rabbitmq']): AmqpConnectionManager {
  const url = `amqp://${encodeURIComponent(config.username)}:${encodeURIComponent(config.password)}@${config.host}:5672`;
  return amqp.connect([url]);
}

export function openChannel(connection: AmqpConnectionManager): ChannelWrapper {
  return connection.createChannel();
}
