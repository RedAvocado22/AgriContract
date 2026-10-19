import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../test/support/fakes';
import { NotificationStatus } from '../domain/notification-log';
import {
  NotificationDeliveryError,
  SendInProgressError,
} from './notification-errors';
import { CLAIM_LEASE_MS, NotificationSender } from './notification-sender';

describe('NotificationSender.send (plan §4.3)', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let sender: NotificationSender;

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    sender = new NotificationSender(logs, email);
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
    it('first delivery: only the claim holder sends; the other retries instead of sending', async () => {
      const release = email.hold(); // delivery A stalls on SMTP after claiming
      const a = sender.send('evt-1', 'a@example.test', 'S', 'B');
      await Promise.resolve();

      // Delivery B of the same event while A is still sending.
      await expect(
        sender.send('evt-1', 'a@example.test', 'S', 'B'),
      ).rejects.toThrow(/another delivery is sending this email/);
      expect(email.sent).toEqual([]);

      release();
      await a;
      expect(email.sent).toHaveLength(1);

      // B's retry after the backoff finds SENT and does nothing.
      await sender.send('evt-1', 'a@example.test', 'S', 'B');
      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('a fresh PENDING row left by another delivery is not sent again', async () => {
      logs.now = 1_000_000;
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);
      logs.now += CLAIM_LEASE_MS - 1;

      await expect(
        sender.send('evt-1', 'a@example.test', 'S', 'B'),
      ).rejects.toThrow(/another delivery/);
      expect(email.sent).toEqual([]);
    });

    it('a PENDING row older than the lease (sender died mid-send) is retaken and sent', async () => {
      logs.now = 1_000_000;
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);
      logs.now += CLAIM_LEASE_MS + 1;

      await sender.send('evt-1', 'a@example.test', 'S', 'B');

      expect(email.sent).toHaveLength(1);
      expect(logs.get('evt-1', 'a@example.test')?.status).toBe(
        NotificationStatus.SENT,
      );
    });

    it('a FAILED row is retaken by exactly one of two deliveries', async () => {
      logs.seed('evt-1', 'a@example.test', NotificationStatus.FAILED);
      const release = email.hold();

      const a = sender.send('evt-1', 'a@example.test', 'S', 'B');
      await Promise.resolve();
      await expect(
        sender.send('evt-1', 'a@example.test', 'S', 'B'),
      ).rejects.toThrow(/another delivery/);

      release();
      await a;
      expect(email.sent).toHaveLength(1);
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

    it('an in-progress claim is a SendInProgressError', async () => {
      logs.seed('evt-1', 'a@example.test', NotificationStatus.PENDING);

      await expect(
        sender.send('evt-1', 'a@example.test', 'S', 'B'),
      ).rejects.toBeInstanceOf(SendInProgressError);
    });
  });
});
