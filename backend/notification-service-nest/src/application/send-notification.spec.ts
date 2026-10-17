import { ConfigService } from '@nestjs/config';
import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../test/support/fakes';
import { validate, type AppConfig } from '../config/env.validation';
import { NotificationStatus } from '../domain/notification-log';
import { ProcessNotificationService } from './process-notification.service';

describe('ProcessNotificationService.sendNotification (plan §4.3)', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let service: ProcessNotificationService;

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    service = new ProcessNotificationService(
      logs,
      email,
      new ConfigService<AppConfig, true>(validate({})),
    );
  });

  it('sends and records SENT with the recipient email as user', async () => {
    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');

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
    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');
    email.sent.length = 0;

    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toEqual([]);
  });

  it('does not send when the insert loses the race on (event_id, user_id)', async () => {
    logs.loseNextInsertRace = true;

    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toEqual([]);
  });

  it('records FAILED with retry_count + 1 and throws when the mail fails', async () => {
    email.failFor.add('a@example.test');

    await expect(
      service.sendNotification('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow('SMTP refused a@example.test');
    expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });

    // Each redelivery of the same event sends again and counts the failure.
    await expect(
      service.sendNotification('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow();
    expect(logs.get('evt-1', 'a@example.test')?.retryCount).toBe(2);
  });

  it('sends again on redelivery after a failure, then records SENT', async () => {
    email.failFor.add('a@example.test');
    await expect(
      service.sendNotification('evt-1', 'a@example.test', 'S', 'B'),
    ).rejects.toThrow();

    email.failFor.clear();
    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');

    expect(email.sent).toHaveLength(1);
    expect(logs.get('evt-1', 'a@example.test')).toMatchObject({
      status: NotificationStatus.SENT,
      retryCount: 1,
    });
  });

  it('a different event to the same recipient is a separate email', async () => {
    await service.sendNotification('evt-1', 'a@example.test', 'S', 'B');
    await service.sendNotification('evt-2', 'a@example.test', 'S', 'B');

    expect(email.sent).toHaveLength(2);
  });
});
