import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from './notification-log';

describe('NotificationLog', () => {
  const create = () =>
    NotificationLog.create(
      'evt-1',
      'buyer@example.test',
      NotificationChannel.EMAIL,
      'subject',
      'body',
    );

  it('keeps the enum values of the Java service', () => {
    expect(Object.values(NotificationStatus)).toEqual([
      'PENDING',
      'SENT',
      'FAILED',
    ]);
    expect(Object.values(NotificationChannel)).toEqual(['EMAIL']);
  });

  it('starts PENDING with no retries and a UUID notification id', () => {
    const log = create();

    expect(log.status).toBe(NotificationStatus.PENDING);
    expect(log.retryCount).toBe(0);
    expect(log.notificationId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(create().notificationId).not.toBe(log.notificationId);
  });

  it('markSent sets SENT without touching the retry count', () => {
    const log = create();
    log.markSent();

    expect(log.status).toBe(NotificationStatus.SENT);
    expect(log.retryCount).toBe(0);
  });

  it('markFailed sets FAILED and counts each failure', () => {
    const log = create();
    log.markFailed();
    log.markFailed();

    expect(log.status).toBe(NotificationStatus.FAILED);
    expect(log.retryCount).toBe(2);
  });

  it('reconstitute keeps the stored state', () => {
    const log = NotificationLog.reconstitute({
      notificationId: 'n-1',
      eventId: 'evt-1',
      recipientEmail: 'seller@example.test',
      channel: NotificationChannel.EMAIL,
      subject: 's',
      body: 'b',
      status: NotificationStatus.FAILED,
      retryCount: 2,
    });
    log.markFailed();

    expect(log.retryCount).toBe(3);
    expect(log.recipientEmail).toBe('seller@example.test');
  });
});
