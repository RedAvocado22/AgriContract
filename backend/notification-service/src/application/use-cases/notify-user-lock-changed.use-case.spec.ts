import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  LOCK_EMAIL,
  LOCK_USER_ID,
  lockCommand,
} from '../../../test/support/commands';
import type { UserLockChangedRequestedCommand } from '../commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyUserLockChangedUseCase,
  userLockChangedBody,
} from './notify-user-lock-changed.use-case';

const KIND = COMMAND_KINDS.userLockChanged;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyUserLockChangedUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
  );
  return { logs, email, useCase };
}

const unlocked = (): UserLockChangedRequestedCommand => ({
  ...lockCommand(),
  action: 'UNLOCKED',
  effectiveUntil: null,
  reasonCode: 'EARLY_UNLOCK_APPROVED',
});

const stillLocked = (): UserLockChangedRequestedCommand => ({
  ...unlocked(),
  effectiveUntil: '2026-11-20T00:00:00Z',
});

describe('NotifyUserLockChangedUseCase', () => {
  it.each([
    ['a lock', lockCommand(), 'Your account is locked'],
    ['an unlock', unlocked(), 'Your account is no longer locked'],
    [
      'an unlock with another lock on',
      stillLocked(),
      'A lock on your account was lifted',
    ],
  ])(
    'emails the user %s, with what happened in the subject',
    async (_c, command, subject) => {
      const { email, useCase } = setup();

      await useCase.execute(command);

      expect(email.sent.map((m) => m.to)).toEqual([LOCK_EMAIL]);
      expect(email.sent[0].subject).toBe(`[AgriContract] ${subject}`);
    },
  );

  it('records the row with its type, template version and platform id', async () => {
    const { logs, useCase } = setup();
    const command = lockCommand();

    await useCase.execute(command);

    expect(
      logs.get({
        eventId: command.eventId,
        recipientEmail: LOCK_EMAIL,
        notificationType: KIND.notificationType,
      }),
    ).toMatchObject({
      status: NotificationStatus.SENT,
      eventType: 'notification.user_lock_changed_requested',
      notificationType: 'USER_LOCK_CHANGED',
      templateVersion: 'user-lock-changed/v1',
      recipientUserId: LOCK_USER_ID,
    });
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(lockCommand());
    await useCase.execute(lockCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(LOCK_EMAIL);

    await expect(useCase.execute(lockCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(lockCommand());
    expect(email.sent).toHaveLength(1);
  });
});

describe('userLockChangedBody (user-lock-changed/v1)', () => {
  it('tells a locked user until when, what the lock stops, and the code', () => {
    expect(userLockChangedBody(lockCommand())).toBe(
      [
        'Your AgriContract account has been locked until:',
        '2026-12-11 11:00:00 Vietnam time (2026-12-11T04:00:00.000Z UTC)',
        '',
        'While it is locked, you cannot create listings or offers, nor sign',
        'contracts.',
        '',
        'Reason code: STRATEGIC_ABANDONMENT',
        '',
      ].join('\n'),
    );
  });

  it('tells an unlocked user no lock is left', () => {
    expect(userLockChangedBody(unlocked())).toBe(
      [
        'The lock on your AgriContract account has been lifted, and no other',
        'lock applies: the lock no longer stops you from creating listings or',
        'offers, nor from signing contracts.',
        '',
        'Reason code: EARLY_UNLOCK_APPROVED',
        '',
      ].join('\n'),
    );
  });

  it('does not call it unlocked when another lock still applies', () => {
    const body = userLockChangedBody(stillLocked());

    expect(body).toBe(
      [
        'A lock on your AgriContract account has been lifted, but another lock',
        'still applies, so your account stays locked until:',
        '2026-11-20 07:00:00 Vietnam time (2026-11-20T00:00:00.000Z UTC)',
        '',
        'While it is locked, you cannot create listings or offers, nor sign',
        'contracts.',
        '',
        'Reason code: EARLY_UNLOCK_APPROVED',
        '',
      ].join('\n'),
    );
    expect(body).not.toContain('no other');
  });

  it('words a lock sent without an end, which user-service never sends', () => {
    const body = userLockChangedBody({
      ...lockCommand(),
      effectiveUntil: null,
    });

    expect(body).toMatch(/^Your AgriContract account has been locked\.\n/);
    expect(body).not.toContain('until');
  });

  it.each(['', '  '])('says no code was given for %p', (reasonCode) => {
    expect(userLockChangedBody({ ...lockCommand(), reasonCode })).toContain(
      'Reason code: (none given)',
    );
  });

  it('converts an instant sent with an offset', () => {
    expect(
      userLockChangedBody({
        ...lockCommand(),
        effectiveUntil: '2026-12-11T11:00:00+07:00',
      }),
    ).toContain(
      '2026-12-11 11:00:00 Vietnam time (2026-12-11T04:00:00.000Z UTC)',
    );
  });

  it.each([
    ['a lock', lockCommand()],
    ['an unlock', unlocked()],
    ['an unlock with another lock on', stillLocked()],
  ])('keeps every line of %s within 76 characters', (_c, command) => {
    for (const line of userLockChangedBody(command).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(76);
    }
  });
});
