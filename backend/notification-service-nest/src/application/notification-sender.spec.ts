import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../test/support/fakes';
import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from '../domain/notification-log';
import {
  ClaimWaitAbortedError,
  NotificationDeliveryError,
  SendInProgressError,
} from './notification-errors';
import {
  CLAIM_HEARTBEAT_MS,
  CLAIM_LEASE_MS,
  CLAIM_POLL_MS,
  CLAIM_WAIT_MS,
  NotificationSender,
  type NotificationSenderOptions,
} from './notification-sender';

describe('NotificationSender.send (plan §4.3)', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let sender: NotificationSender;
  /** The fake clock below, as sender options. */
  let fakeClock: NotificationSenderOptions;
  /** Fake clock of the claim wait: each sleep advances it and the repository clock, then runs onSleep(n). */
  let clock: {
    t: number;
    sleeps: number[];
    onSleep?: (n: number) => void | Promise<void>;
  };

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    clock = { t: 0, sleeps: [] };
    fakeClock = {
      now: () => clock.t,
      sleep: async (ms) => {
        clock.sleeps.push(ms);
        clock.t += ms;
        logs.now += ms;
        await clock.onSleep?.(clock.sleeps.length);
      },
    };
    sender = new NotificationSender(logs, email, fakeClock);
  });

  it('sends and records SENT with the recipient email as user', async () => {
    await sender.send('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toEqual([
      { to: 'a@example.test', subject: 'S', body: 'B' },
    ]);
    expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
      recipientEmail: 'a@example.test',
      status: NotificationStatus.SENT,
      retryCount: 0,
      subject: 'S',
      body: 'B',
    });
  });

  it('skips a recipient already SENT for this event', async () => {
    await sender.send('evt-1', 'a@example.test', 'S', 'B');
    email.sent.length = 0;

    await sender.send('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toEqual([]);
  });

  it('records FAILED with retry_count + 1 and throws when the mail fails', async () => {
    email.failFor.add('a@example.test');

    await expect(
      sender.send('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow();
    expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });

    // Each redelivery retakes the FAILED row, sends again and counts the failure.
    await expect(
      sender.send('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow();
    expect(logs.get('evt-1', 'a@example.test')?.retryCount).toBe(2);
  });

  it('sends again on redelivery after a failure, then records SENT', async () => {
    email.failFor.add('a@example.test');
    await expect(
      sender.send('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow();

    email.failFor.clear();
    await sender.send('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toHaveLength(1);
    expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
      status: NotificationStatus.SENT,
      retryCount: 1,
    });
  });

  it('a different event to the same recipient is a separate email', async () => {
    await sender.send('evt-1', 'a@example.test', 'S', 'B');
    await sender.send('evt-2', 'a@example.test', 'S', 'B');

    expect(email.sent).toHaveLength(2);
  });

  describe('concurrent deliveries of the same event (review #172)', () => {
    it('first delivery: only the claim holder sends; the other waits, finds SENT and sends nothing', async () => {
      const release = email.hold(); // delivery A stalls on SMTP after claiming
      const a = sender.send('evt-1', 'a@example.test', 'S', 'B');
      await Promise.resolve();
      clock.onSleep = async (n) => {
        expect(email.sent).toEqual([]); // B never sends while A holds the claim
        if (n === 3) {
          release();
          await a;
        }
      };

      // Delivery B of the same event while A is still sending.
      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(clock.sleeps).toEqual([1000, 1000, 1000]);
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('a PENDING row older than the lease (sender died mid-send) is retaken and sent at once', async () => {
      logs.now = 1_000_000;
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);
      logs.now += CLAIM_LEASE_MS + 1;

      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(clock.sleeps).toEqual([]);
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('a FAILED row is retaken by exactly one of two deliveries; the other then finds SENT', async () => {
      logs.seed('evt-1', 'a@example.test', NotificationStatus.FAILED);
      const release = email.hold();

      const a = sender.send('evt-1', 'a@example.test', 'S', 'B');
      await Promise.resolve();
      clock.onSleep = async () => {
        release();
        await a;
      };
      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(clock.sleeps).toEqual([1000]);
      expect(email.sent).toHaveLength(1);
    });
  });

  describe('waiting for a live claim of another delivery (injected clock and sleep)', () => {
    beforeEach(() => {
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);
    });

    it('waits for 50s at most, polling every 1s', () => {
      expect(CLAIM_WAIT_MS).toBe(50_000);
      expect(CLAIM_POLL_MS).toBe(1_000);
    });

    it('SENT meanwhile: skips the recipient without sending', async () => {
      clock.onSleep = (n) => {
        if (n === 5) void logs.markSent('evt-1', 'a@example.test');
      };

      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(clock.sleeps).toHaveLength(5);
      expect(email.sent).toEqual([]);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('FAILED meanwhile: takes the claim over and sends', async () => {
      clock.onSleep = (n) => {
        if (n === 2) void logs.markFailed('evt-1', 'a@example.test');
      };

      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(clock.sleeps).toHaveLength(2);
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
        status: NotificationStatus.SENT,
        retryCount: 1,
      });
    });

    it('holder died (no heartbeat): takes the claim over once it is older than the 45s lease, and sends', async () => {
      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      // Claimed at t=0; stale (updated_at < now - lease) from 45.001s on, seen at the 46th look.
      expect(clock.sleeps).toHaveLength(46);
      expect(clock.t).toBe(46_000);
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('holder still alive after 50s (heartbeat): throws a retryable SendInProgressError without sending', async () => {
      clock.onSleep = () => void logs.heartbeat('evt-1', 'a@example.test'); // the holder's heartbeat keeps the claim fresh

      const failure = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await expect(failure).rejects.toBeInstanceOf(SendInProgressError);
      await expect(failure).rejects.toThrow(
        'Event evt-1: another delivery is sending this email; retrying later',
      );
      expect(clock.sleeps).toEqual(Array<number>(50).fill(1000));
      expect(clock.t).toBe(CLAIM_WAIT_MS);
      expect(email.sent).toEqual([]);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.PENDING,
      );
    });

    describe('shutdown during the wait (review of #206)', () => {
      let shutdown: AbortController;

      beforeEach(() => {
        shutdown = new AbortController();
        sender = new NotificationSender(logs, email, {
          ...fakeClock,
          shutdown: shutdown.signal,
        });
      });

      it('holder still alive: one more look, then a ClaimWaitAbortedError without sending', async () => {
        clock.onSleep = (n) => {
          void logs.heartbeat('evt-1', 'a@example.test');
          if (n === 3) shutdown.abort();
        };

        const failure = sender.send('evt-1', 'a@example.test', 'S', 'B');

        await expect(failure).rejects.toBeInstanceOf(ClaimWaitAbortedError);
        await expect(failure).rejects.toThrow(
          'Event evt-1: shutdown began while another delivery was sending this email',
        );
        expect(clock.sleeps).toHaveLength(3);
        expect(email.sent).toEqual([]);
        expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
          NotificationStatus.PENDING,
        );
      });

      it('SENT on the last look: skips the recipient, no error', async () => {
        clock.onSleep = (n) => {
          if (n !== 3) return;
          void logs.markSent('evt-1', 'a@example.test');
          shutdown.abort();
        };

        await sender.send('evt-1', 'a@example.test', 'S', 'B');

        expect(clock.sleeps).toHaveLength(3);
        expect(email.sent).toEqual([]);
      });

      it('the default sleep ends as soon as shutdown begins, not after the 1s poll', async () => {
        sender = new NotificationSender(logs, email, {
          shutdown: shutdown.signal,
        });
        const started = performance.now();

        const failure = sender.send('evt-1', 'a@example.test', 'S', 'B');
        setTimeout(() => shutdown.abort(), 20);

        await expect(failure).rejects.toBeInstanceOf(ClaimWaitAbortedError);
        expect(performance.now() - started).toBeLessThan(CLAIM_POLL_MS / 2);
      });
    });
  });

  describe('claim heartbeat while sending (fake timers)', () => {
    let logger: { error: jest.Mock };

    beforeEach(() => {
      jest.useFakeTimers();
      logger = { error: jest.fn() };
      sender = new NotificationSender(logs, email, { logger });
    });
    afterEach(() => jest.useRealTimers());

    /** Advances the timers and the fake repository clock together. */
    const tick = async (ms: number) => {
      logs.now += ms;
      await jest.advanceTimersByTimeAsync(ms);
    };

    it('uses a 45s lease refreshed every 15s', () => {
      expect(CLAIM_LEASE_MS).toBe(45_000);
      expect(CLAIM_HEARTBEAT_MS).toBe(15_000);
    });

    it('refreshes the claim every 15s while the send is in progress', async () => {
      const heartbeat = jest.spyOn(logs, 'heartbeat');
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await tick(CLAIM_HEARTBEAT_MS - 1);
      expect(heartbeat).not.toHaveBeenCalled();
      await tick(1);
      expect(heartbeat).toHaveBeenCalledTimes(1);
      expect(heartbeat).toHaveBeenCalledWith('evt-1', 'a@example.test');
      await tick(CLAIM_HEARTBEAT_MS);
      expect(heartbeat).toHaveBeenCalledTimes(2);

      release();
      await sending;
    });

    it('keeps a long send claimed past the lease: another delivery cannot take it over', async () => {
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await tick(CLAIM_LEASE_MS + CLAIM_HEARTBEAT_MS); // 60s after the claim, last beat at 60s

      await expect(
        logs.claim(
          NotificationLog.create(
            'evt-1',
            'a@example.test',
            NotificationChannel.EMAIL,
            'S',
            'B',
          ),
          CLAIM_LEASE_MS,
        ),
      ).resolves.toBe('IN_PROGRESS');

      release();
      await sending;
      expect(email.sent).toHaveLength(1);
    });

    it('stops beating once the send succeeded', async () => {
      const heartbeat = jest.spyOn(logs, 'heartbeat');
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');
      await tick(CLAIM_HEARTBEAT_MS);
      release();
      await sending;
      expect(heartbeat).toHaveBeenCalledTimes(1);

      await tick(10 * CLAIM_HEARTBEAT_MS);

      expect(heartbeat).toHaveBeenCalledTimes(1);
      expect(jest.getTimerCount()).toBe(0);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('stops beating once the send failed', async () => {
      const heartbeat = jest.spyOn(logs, 'heartbeat');
      email.failFor.add('a@example.test');
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');
      const failed = expect(sending).rejects.toBeInstanceOf(
        NotificationDeliveryError,
      );
      await tick(CLAIM_HEARTBEAT_MS);
      release();
      await failed;
      expect(heartbeat).toHaveBeenCalledTimes(1);

      await tick(10 * CLAIM_HEARTBEAT_MS);

      expect(heartbeat).toHaveBeenCalledTimes(1);
      expect(jest.getTimerCount()).toBe(0);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.FAILED,
      );
    });

    it('a failed beat is logged as an error with the event id and code only; the send goes on', async () => {
      jest.spyOn(logs, 'heartbeat').mockRejectedValueOnce(
        Object.assign(new Error("Lock wait for 'a@example.test'"), {
          code: 'ER_LOCK_WAIT_TIMEOUT',
        }),
      );
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await tick(CLAIM_HEARTBEAT_MS);
      release();
      await sending;

      expect(logger.error).toHaveBeenCalledTimes(1);
      expect(logger.error).toHaveBeenCalledWith(
        'Event evt-1: claim heartbeat failed (ER_LOCK_WAIT_TIMEOUT)',
      );
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('does not start a beat while the previous one still waits for the database', async () => {
      let unblock!: () => void;
      const heartbeat = jest
        .spyOn(logs, 'heartbeat')
        .mockImplementationOnce(
          () => new Promise<void>((resolve) => (unblock = resolve)),
        );
      const release = email.hold();
      const sending = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await tick(3 * CLAIM_HEARTBEAT_MS);
      expect(heartbeat).toHaveBeenCalledTimes(1);
      unblock();
      await tick(CLAIM_HEARTBEAT_MS);
      expect(heartbeat).toHaveBeenCalledTimes(2);

      release();
      await sending;
    });
  });

  describe('error messages are safe to log (review #172)', () => {
    it('a failed send names the event and the code, not the recipient', async () => {
      email.failFor.add('a@example.test');

      await expect(
        sender.send('evt-1', 'a@example.test', 'S', 'B'),
      ).rejects.toThrow(
        new NotificationDeliveryError('evt-1', 'email', 'EENVELOPE/550'),
      );
    });

    it('a repository failure is reduced to its code; the driver text is dropped', async () => {
      jest
        .spyOn(logs, 'claim')
        .mockRejectedValue(
          Object.assign(
            new Error("Incorrect string value: 'a@example.test' for column"),
            { code: 'ER_TRUNCATED_WRONG_VALUE' },
          ),
        );

      const failure = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await expect(failure).rejects.toBeInstanceOf(NotificationDeliveryError);
      await expect(failure).rejects.toThrow(
        'Event evt-1: notification log write failed (ER_TRUNCATED_WRONG_VALUE)',
      );
      expect(email.sent).toEqual([]);
    });

    it('a claim still live after the wait is a SendInProgressError naming only the event', async () => {
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);
      clock.onSleep = () => void logs.heartbeat('evt-1', 'a@example.test');

      const failure = sender.send('evt-1', 'a@example.test', 'S', 'B');

      await expect(failure).rejects.toBeInstanceOf(SendInProgressError);
      await expect(failure).rejects.not.toThrow(/a@example\.test/);
    });
  });
});
