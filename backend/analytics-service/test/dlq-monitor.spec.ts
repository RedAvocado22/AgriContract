import assert from 'node:assert/strict';
import { DlqMonitor, type QueueProbe } from '../src/infrastructure/messaging/dlqMonitor';
import { SUBSCRIPTIONS, deadLetterQueueName } from '../src/infrastructure/messaging/topology';
import { recordingLogger } from './fakes';
import { deepEqual } from './deepEqual';

function probe(depths: Record<string, number>, failing: string[] = []): QueueProbe & { asked: string[] } {
  const asked: string[] = [];
  return {
    asked,
    async checkQueue(queue: string) {
      asked.push(queue);
      if (failing.includes(queue)) throw new Error(`NOT_FOUND - no queue '${queue}'`);
      return { messageCount: depths[queue] ?? 0 };
    },
  };
}

describe('DLQ monitor', () => {
  it('checks the DLQ of every subscribed event', async () => {
    const p = probe({});
    await new DlqMonitor(p, { intervalMs: 1000, logger: recordingLogger() }).checkOnce();
    deepEqual(p.asked, SUBSCRIPTIONS.map((s) => deadLetterQueueName(s.eventType)));
  });

  it('a non-empty DLQ warns with queue and depth', async () => {
    const logger = recordingLogger();
    const queue = deadLetterQueueName('milestone.settled');
    const depths = await new DlqMonitor(probe({ [queue]: 3 }), { intervalMs: 1000, logger }).checkOnce();
    deepEqual(depths.find((d) => d.queue === queue), { queue, depth: 3 });
    const warnings = logger.entries.filter((e) => e.level === 'warn');
    assert.equal(warnings.length, 1);
    deepEqual(warnings[0]!.meta, { queue, depth: 3 });
  });

  it('empty DLQs do not warn', async () => {
    const logger = recordingLogger();
    await new DlqMonitor(probe({}), { intervalMs: 1000, logger }).checkOnce();
    assert.equal(logger.entries.length, 0);
  });

  it('a failing check is logged and the remaining queues are still checked', async () => {
    const logger = recordingLogger();
    const first = deadLetterQueueName(SUBSCRIPTIONS[0]!.eventType);
    const p = probe({}, [first]);
    await new DlqMonitor(p, { intervalMs: 1000, logger }).checkOnce();
    assert.equal(p.asked.length, SUBSCRIPTIONS.length);
    assert.equal(logger.entries.filter((e) => e.msg === 'Could not read dead-letter queue depth').length, 1);
  });

  it('start() checks right away and repeats; stop() ends it; one pass at a time', async () => {
    let concurrent = 0;
    let maxConcurrent = 0;
    let passes = 0;
    const slow: QueueProbe = {
      async checkQueue() {
        concurrent += 1;
        maxConcurrent = Math.max(maxConcurrent, concurrent);
        passes += 1;
        await new Promise((r) => setTimeout(r, 5));
        concurrent -= 1;
        return { messageCount: 0 };
      },
    };
    const monitor = new DlqMonitor(slow, { intervalMs: 1000, logger: recordingLogger(), queues: ['q1'] });
    monitor.start();
    monitor.start(); // a second start() must not create a second timer
    await new Promise((r) => setTimeout(r, 30));
    monitor.stop();
    const afterStop = passes;
    await new Promise((r) => setTimeout(r, 30));
    assert.equal(afterStop, 1, 'first pass ran immediately');
    assert.equal(passes, afterStop, 'nothing runs after stop()');
    assert.equal(maxConcurrent, 1);
  });
});
