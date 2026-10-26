import { Injectable, Logger } from '@nestjs/common';
import { Interval } from '@nestjs/schedule';
import { describeError } from '../describe-error';
import { AmqpService } from './amqp.provider';
import { dlqNames } from './topology';

/** Every 5 minutes, as the Java DlqMonitor (fixedDelay = 300000). */
export const DLQ_CHECK_INTERVAL_MS = 300_000;

/**
 * Warns while a DLQ holds messages: those need a person (fix the cause, then
 * replay the original message so its eventId is kept).
 */
@Injectable()
export class DlqMonitor {
  private readonly logger = new Logger(DlqMonitor.name);

  constructor(private readonly amqp: AmqpService) {}

  @Interval(DLQ_CHECK_INTERVAL_MS)
  async checkDlqDepth(): Promise<void> {
    if (!this.amqp.isHealthy()) {
      this.logger.warn('DLQ check skipped: RabbitMQ channel is not ready');
      return;
    }
    const channel = this.amqp.getChannel();
    for (const dlq of dlqNames()) {
      try {
        const { messageCount } = await channel.checkQueue(dlq);
        if (messageCount > 0) {
          this.logger.warn(
            `DLQ ${dlq} has ${messageCount} message(s) — need manual fix`,
          );
        }
      } catch (err) {
        this.logger.warn(`DLQ check of ${dlq} failed: ${describeError(err)}`);
        return; // the channel may be gone; try again next interval
      }
    }
  }
}
