import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  KYC_EMAIL,
  KYC_USER_ID,
  kycCommand,
} from '../../../test/support/commands';
import type { UserKycResultRequestedCommand } from '../commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyUserKycResultUseCase,
  userKycResultBody,
} from './notify-user-kyc-result.use-case';

const KIND = COMMAND_KINDS.userKycResult;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyUserKycResultUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
  );
  return { logs, email, useCase };
}

const verified = (): UserKycResultRequestedCommand => ({
  ...kycCommand(),
  result: 'VERIFIED',
  reason: null,
});

describe('NotifyUserKycResultUseCase', () => {
  it.each([
    ['a rejection', kycCommand(), 'Your organisation was not verified'],
    ['a verification', verified(), 'Your organisation is verified'],
  ])(
    'emails the user %s, with the result in the subject',
    async (_c, command, subject) => {
      const { email, useCase } = setup();

      await useCase.execute(command);

      expect(email.sent.map((m) => m.to)).toEqual([KYC_EMAIL]);
      expect(email.sent[0].subject).toBe(`[AgriContract] ${subject}`);
    },
  );

  it('records the row with its type, template version and platform id', async () => {
    const { logs, useCase } = setup();
    const command = kycCommand();

    await useCase.execute(command);

    expect(
      logs.get({
        eventId: command.eventId,
        recipientEmail: KYC_EMAIL,
        notificationType: KIND.notificationType,
      }),
    ).toMatchObject({
      status: NotificationStatus.SENT,
      eventType: 'notification.user_kyc_result_requested',
      notificationType: 'USER_KYC_RESULT',
      templateVersion: 'user-kyc-result/v1',
      recipientUserId: KYC_USER_ID,
    });
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(kycCommand());
    await useCase.execute(kycCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a second decision is a second command, and a second email', async () => {
    const { email, useCase } = setup();

    await useCase.execute(kycCommand());
    await useCase.execute({
      ...verified(),
      eventId: '8a7b6c5d-4e3f-4a2b-9c1d-0e9f8a7b6c5d',
    });

    expect(email.sent.map((m) => m.subject)).toEqual([
      '[AgriContract] Your organisation was not verified',
      '[AgriContract] Your organisation is verified',
    ]);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(KYC_EMAIL);

    await expect(useCase.execute(kycCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(kycCommand());
    expect(email.sent).toHaveLength(1);
  });
});

describe('userKycResultBody (user-kyc-result/v1)', () => {
  it('tells a rejected user which organisation, why, and when', () => {
    expect(userKycResultBody(kycCommand())).toBe(
      [
        'The KYC verification of your organisation on AgriContract is done.',
        'This organisation has not been verified:',
        '',
        '  Dak Lak Coffee Cooperative',
        '',
        'Reason given:',
        '  The business licence is not legible.',
        '',
        'Decided at:',
        '2026-11-11 09:30:00 Vietnam time (2026-11-11T02:30:00.123Z UTC)',
        '',
      ].join('\n'),
    );
  });

  it('tells a verified user which organisation and when, with no reason', () => {
    expect(userKycResultBody(verified())).toBe(
      [
        'The KYC verification of your organisation on AgriContract is done.',
        'This organisation has been verified:',
        '',
        '  Dak Lak Coffee Cooperative',
        '',
        'Decided at:',
        '2026-11-11 09:30:00 Vietnam time (2026-11-11T02:30:00.123Z UTC)',
        '',
      ].join('\n'),
    );
  });

  it('shows no reason on a verification, even one sent with a reason', () => {
    expect(
      userKycResultBody({ ...verified(), reason: 'all good' }),
    ).not.toContain('all good');
  });

  it.each([null, '', '   '])(
    'says no reason was given for a rejection whose reason is %p',
    (reason) => {
      const body = userKycResultBody({ ...kycCommand(), reason });

      expect(body).toContain('No reason was given.');
      expect(body).not.toContain('Reason given:');
    },
  );

  it('indents each line of a reason written on several lines', () => {
    const body = userKycResultBody({
      ...kycCommand(),
      reason: 'The licence is not legible.\r\nThe tax code does not match.\n',
    });

    expect(body).toContain(
      [
        'Reason given:',
        '  The licence is not legible.',
        '  The tax code does not match.',
        '',
        'Decided at:',
      ].join('\n'),
    );
  });

  it('quotes a Vietnamese organisation name as sent', () => {
    expect(
      userKycResultBody({
        ...kycCommand(),
        organizationName: 'Hợp tác xã Cà phê Đắk Lắk',
      }),
    ).toContain('\n  Hợp tác xã Cà phê Đắk Lắk\n');
  });

  it('names no organisation it was not sent', () => {
    expect(
      userKycResultBody({ ...kycCommand(), organizationName: ' ' }),
    ).toContain('\n  (no organisation name on record)\n');
  });

  it('converts an instant sent with an offset', () => {
    expect(
      userKycResultBody({
        ...kycCommand(),
        decidedAt: '2026-11-11T09:30:00+07:00',
      }),
    ).toContain(
      '2026-11-11 09:30:00 Vietnam time (2026-11-11T02:30:00.000Z UTC)',
    );
  });

  it.each([
    ['a rejection', kycCommand()],
    ['a verification', verified()],
  ])('keeps every fixed line of %s within 76 characters', (_c, command) => {
    for (const line of userKycResultBody(command).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(76);
    }
  });
});
