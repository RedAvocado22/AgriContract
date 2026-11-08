import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from './notification-log';

describe('NotificationLog', () => {
  const KIND = {
    eventType: 'contract.signed',
    notificationType: 'CONTRACT_SIGNED',
    templateVersion: 'contract-signed/p1',
  };
  const create = (recipientUserId?: string) =>
    NotificationLog.create(
      'evt-1',
      KIND,
      'buyer@example.test',
      NotificationChannel.EMAIL,
      'subject',
      'body',
      recipientUserId,
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

  it('is keyed by event, recipient and notification type', () => {
    const log = create();

    expect(log.eventType).toBe('contract.signed');
    expect(log.key).toEqual({
      eventId: 'evt-1',
      recipientEmail: 'buyer@example.test',
      notificationType: 'CONTRACT_SIGNED',
    });
  });

  it("takes the kind's template version, the evidence of what was sent", () => {
    expect(create().templateVersion).toBe('contract-signed/p1');
  });

  it('carries the recipient platform id when the sender has one, null otherwise', () => {
    expect(create('user-7').recipientUserId).toBe('user-7');
    expect(create().recipientUserId).toBeNull(); // every Phase 1 event
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
      eventType: 'contract.signed',
      notificationType: 'CONTRACT_SIGNED',
      recipientUserId: 'user-9',
      recipientEmail: 'seller@example.test',
      channel: NotificationChannel.EMAIL,
      subject: 's',
      body: 'b',
      templateVersion: 'contract-signed/p1',
      status: NotificationStatus.FAILED,
      retryCount: 2,
    });
    log.markFailed();

    expect(log.retryCount).toBe(3);
    expect(log.recipientEmail).toBe('seller@example.test');
    expect(log.recipientUserId).toBe('user-9');
    expect(log.templateVersion).toBe('contract-signed/p1');
  });
});
