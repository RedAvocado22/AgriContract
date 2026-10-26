import { Logger } from '@nestjs/common';
import { SCHEDULE_INTERVAL_OPTIONS } from '@nestjs/schedule/dist/schedule.constants';
import type { AmqpService } from './amqp.provider';
import { DLQ_CHECK_INTERVAL_MS, DlqMonitor } from './dlq-monitor';
import { dlqNames } from './topology';

function monitorWith(
  counts: Record<string, number>,
  options: { healthy?: boolean; failOn?: string } = {},
) {
  const checkQueue = jest.fn((queue: string) =>
    queue === options.failOn
      ? Promise.reject(new Error('NOT_FOUND - no queue'))
      : Promise.resolve({ queue, messageCount: counts[queue] ?? 0 }),
  );
  const amqp = {
    isHealthy: () => options.healthy ?? true,
    getChannel: () => ({ checkQueue }),
  } as unknown as AmqpService;
  return { monitor: new DlqMonitor(amqp), checkQueue };
}

describe('DlqMonitor', () => {
  let warn: jest.SpyInstance;

  beforeEach(() => {
    warn = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
  });
  afterEach(() => jest.restoreAllMocks());

  it('runs every 5 minutes, like the Java DlqMonitor', () => {
    expect(DLQ_CHECK_INTERVAL_MS).toBe(300_000);
    const method = Object.getOwnPropertyDescriptor(
      DlqMonitor.prototype,
      'checkDlqDepth',
    )?.value as object;
    const options = Reflect.getMetadata(SCHEDULE_INTERVAL_OPTIONS, method) as {
      timeout: number;
    };
    expect(options.timeout).toBe(300_000);
  });

  it('checks all 9 DLQs and stays quiet when they are empty', async () => {
    const { monitor, checkQueue } = monitorWith({});

    await monitor.checkDlqDepth();

    expect(checkQueue.mock.calls.map(([q]) => q)).toEqual(dlqNames());
    expect(warn).not.toHaveBeenCalled();
  });

  it('warns for each DLQ that holds messages', async () => {
    const { monitor } = monitorWith({
      'notification-svc.contract.signed.dlq': 2,
      'notification-svc.escrow.penalized.dlq': 1,
    });

    await monitor.checkDlqDepth();

    expect(warn.mock.calls.map(([m]) => m as string)).toEqual([
      'DLQ notification-svc.contract.signed.dlq has 2 message(s) — need manual fix',
      'DLQ notification-svc.escrow.penalized.dlq has 1 message(s) — need manual fix',
    ]);
  });

  it('skips the check without crashing while the channel is not ready', async () => {
    const { monitor, checkQueue } = monitorWith({}, { healthy: false });

    await expect(monitor.checkDlqDepth()).resolves.toBeUndefined();

    expect(checkQueue).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalledWith(
      'DLQ check skipped: RabbitMQ channel is not ready',
    );
  });

  it('logs a failed check and stops this round instead of throwing', async () => {
    const { monitor, checkQueue } = monitorWith(
      {},
      { failOn: 'notification-svc.contract.delivered.dlq' },
    );

    await expect(monitor.checkDlqDepth()).resolves.toBeUndefined();

    expect(checkQueue).toHaveBeenCalledTimes(2);
    expect(warn).toHaveBeenCalledWith(
      'DLQ check of notification-svc.contract.delivered.dlq failed: NOT_FOUND - no queue',
    );
  });
});
