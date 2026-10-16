import type { Logger } from '@agricontract/eventing';
import { SUBSCRIPTIONS, deadLetterQueueName } from './topology.js';

/** Subset of an amqplib Channel. Use a dedicated channel: a failed checkQueue closes the channel it ran on. */
export interface QueueProbe {
  checkQueue(queue: string): Promise<{ messageCount: number }>;
}

export interface DlqDepth {
  queue: string;
  depth: number;
}

export interface DlqMonitorOptions {
  intervalMs: number;
  logger: Logger;
  queues?: readonly string[];
}

/**
 * Review rules §3: every consumer queue has a DLQ and a monitor warning while it is not empty. Polls the
 * depth of each analytics DLQ on a fixed interval (one pass at a time, so it is bounded) and logs a
 * warning with queue name and depth only, never message content.
 */
export class DlqMonitor {
  private timer: NodeJS.Timeout | undefined;
  private running = false;
  private readonly queues: readonly string[];

  constructor(
    private readonly probe: QueueProbe,
    private readonly options: DlqMonitorOptions,
  ) {
    this.queues = options.queues ?? SUBSCRIPTIONS.map((s) => deadLetterQueueName(s.eventType));
  }

  /** One pass over all DLQs. Never throws: a failed probe is logged and the next queue is still checked. */
  async checkOnce(): Promise<DlqDepth[]> {
    const depths: DlqDepth[] = [];
    for (const queue of this.queues) {
      try {
        const { messageCount } = await this.probe.checkQueue(queue);
        depths.push({ queue, depth: messageCount });
        if (messageCount > 0) {
          this.options.logger.warn('Dead-letter queue is not empty: events need a developer', { queue, depth: messageCount });
        }
      } catch (err) {
        this.options.logger.warn('Could not read dead-letter queue depth', {
          queue,
          error: err instanceof Error ? err.message : String(err),
        });
      }
    }
    return depths;
  }

  start(): void {
    if (this.timer) return;
    const tick = () => {
      if (this.running) return; // previous pass still running: never pile up
      this.running = true;
      this.checkOnce()
        .catch(() => {})
        .finally(() => {
          this.running = false;
        });
    };
    tick();
    this.timer = setInterval(tick, Math.max(1000, this.options.intervalMs));
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }
}
