import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  OPERATOR_EMAIL,
  WAITING_CONTRACT_ID,
  WAITING_MILESTONE_ID,
  waitingCommand,
} from '../../../test/support/commands';
import type {
  Level15AssignmentWaitingRequestedCommand,
  Level15WaitingStage,
} from '../commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { OperatorEmailNotConfiguredError } from '../notification-errors';
import { NotificationSender } from '../notification-sender';
import {
  NotifyLevel15AssignmentWaitingUseCase,
  level15AssignmentWaitingBody,
} from './notify-level15-assignment-waiting.use-case';

function setup(operatorEmail: string | null = OPERATOR_EMAIL) {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyLevel15AssignmentWaitingUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
    operatorEmail,
  );
  return { logs, email, useCase };
}

/** The same wait at a later stage: a new command, so a new eventId. */
const at = (
  stage: Level15WaitingStage,
  eventId: string,
): Level15AssignmentWaitingRequestedCommand => ({
  ...waitingCommand(),
  stage,
  eventId,
});

const STILL = at('STILL_WAITING', '0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d');
const ASSIGNED = at('ASSIGNED', '9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f');

describe('NotifyLevel15AssignmentWaitingUseCase', () => {
  it.each([
    [
      'WAITING_STARTED',
      waitingCommand(),
      'A Level 1.5 inspection is waiting for an inspector',
      COMMAND_KINDS.level15WaitingStarted,
    ],
    [
      'STILL_WAITING',
      STILL,
      'A Level 1.5 inspection is still waiting for an inspector',
      COMMAND_KINDS.level15StillWaiting,
    ],
    [
      'ASSIGNED',
      ASSIGNED,
      'A waiting Level 1.5 inspection now has an inspector',
      COMMAND_KINDS.level15AssignedAfterWait,
    ],
  ])(
    'mails %s to the operator address with its own template',
    async (_stage, command, subject, kind) => {
      const { logs, email, useCase } = setup();

      await useCase.execute(command);

      expect(email.sent.map((m) => m.to)).toEqual([OPERATOR_EMAIL]);
      expect(email.sent[0].subject).toBe(`[AgriContract] ${subject}`);
      expect(
        logs.get({
          eventId: command.eventId,
          recipientEmail: OPERATOR_EMAIL,
          notificationType: kind.notificationType,
        }),
      ).toMatchObject({
        status: NotificationStatus.SENT,
        eventType: 'notification.level1_5_assignment_waiting_requested',
        notificationType: kind.notificationType,
        templateVersion: kind.templateVersion,
        // An operator address, not a platform account.
        recipientUserId: null,
      });
    },
  );

  it('the three stages of one wait are three emails', async () => {
    const { email, useCase } = setup();

    await useCase.execute(waitingCommand());
    await useCase.execute(STILL);
    await useCase.execute(ASSIGNED);

    expect(email.sent).toHaveLength(3);
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(waitingCommand());
    await useCase.execute(waitingCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(OPERATOR_EMAIL);

    await expect(useCase.execute(waitingCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(waitingCommand());
    expect(email.sent).toHaveLength(1);
  });

  it('without an operator address, fails naming OPERATOR_EMAIL and records nothing', async () => {
    const { logs, email, useCase } = setup(null);
    const command = waitingCommand();

    const failure = useCase.execute(command);

    await expect(failure).rejects.toBeInstanceOf(
      OperatorEmailNotConfiguredError,
    );
    await expect(failure).rejects.toThrow(
      `Event ${command.eventId}: OPERATOR_EMAIL is not set`,
    );
    expect(email.sent).toEqual([]);
    expect(logs.size).toBe(0);
  });
});

describe('level15AssignmentWaitingBody', () => {
  const DETAILS = [
    `Contract:  ${WAITING_CONTRACT_ID}`,
    `Milestone: ${WAITING_MILESTONE_ID}`,
    'Waiting since:',
    '2026-11-16 10:00:00 Vietnam time (2026-11-16T03:00:00.000Z UTC)',
  ];
  const WHAT_TO_DO = [
    'What you can do:',
    '- Ask an Admin to create more INSPECTOR accounts. The request is assigned',
    '  automatically, at random, as soon as an inspector is eligible.',
    '- Do not pick an inspector by hand: there is no manual assignment.',
  ];

  it('WAITING_STARTED (level1-5-waiting-started/v1): why, what happens next, what to do', () => {
    expect(level15AssignmentWaitingBody(waitingCommand())).toBe(
      [
        'A Level 1.5 inspection request could not be assigned to an inspector.',
        'No inspector is eligible for it.',
        '',
        'The request is waiting, and inspection-service keeps trying to assign',
        'it. You will be reminded at most once per business day while it',
        'waits, and told when it is assigned.',
        '',
        ...DETAILS,
        '',
        ...WHAT_TO_DO,
        '',
      ].join('\n'),
    );
  });

  it('STILL_WAITING (level1-5-still-waiting/v1): the reminder, with the same details', () => {
    expect(
      level15AssignmentWaitingBody({ ...STILL, reason: 'ALL_EXCLUDED' }),
    ).toBe(
      [
        'A Level 1.5 inspection request is still waiting for an inspector.',
        'Every eligible inspector has already been assigned this milestone',
        'once, so none of them can take it again.',
        '',
        ...DETAILS,
        '',
        ...WHAT_TO_DO,
        '',
      ].join('\n'),
    );
  });

  it('ASSIGNED (level1-5-assigned-after-wait/v1): no action, no inspector named', () => {
    expect(level15AssignmentWaitingBody(ASSIGNED)).toBe(
      [
        'A Level 1.5 inspection request that was waiting for an inspector has',
        'now been assigned to one. Nothing more is needed from you.',
        '',
        'The report deadline counts from the assignment, not from the start',
        'of the wait.',
        '',
        ...DETAILS,
        '',
      ].join('\n'),
    );
  });

  it.each(['WAITING_STARTED', 'STILL_WAITING', 'ASSIGNED'] as const)(
    'keeps every line of %s within 76 characters',
    (stage) => {
      for (const reason of ['NO_ELIGIBLE', 'ALL_EXCLUDED'] as const) {
        const body = level15AssignmentWaitingBody({
          ...waitingCommand(),
          stage,
          reason,
        });
        for (const line of body.split('\n')) {
          expect(line.length).toBeLessThanOrEqual(76);
        }
      }
    },
  );
});
