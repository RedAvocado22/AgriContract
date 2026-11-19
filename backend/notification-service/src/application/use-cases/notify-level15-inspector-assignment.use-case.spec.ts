import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  INSPECTOR_COMMISSION_ID,
  INSPECTOR_CONTRACT_ID,
  INSPECTOR_MILESTONE_ID,
  INSPECTOR_RECIPIENT,
  inspectorCommand,
} from '../../../test/support/commands';
import type { Level15InspectorAssignmentRequestedCommand } from '../commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyLevel15InspectorAssignmentUseCase,
  level15InspectorAssignmentBody,
} from './notify-level15-inspector-assignment.use-case';

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyLevel15InspectorAssignmentUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
  );
  return { logs, email, useCase };
}

/** The same assignment, cancelled: a new command, so a new eventId. */
const CANCELLED: Level15InspectorAssignmentRequestedCommand = {
  ...inspectorCommand(),
  stage: 'CANCELLED',
  eventId: '8b9c0d1e-2f3a-4b4c-9d5e-6f7a8b9c0d1e',
};

describe('NotifyLevel15InspectorAssignmentUseCase', () => {
  it.each([
    [
      'ASSIGNED',
      inspectorCommand(),
      'You have been assigned a Level 1.5 inspection',
      COMMAND_KINDS.level15InspectorAssigned,
    ],
    [
      'CANCELLED',
      CANCELLED,
      'Your Level 1.5 inspection was cancelled',
      COMMAND_KINDS.level15InspectorCancelled,
    ],
  ])(
    'mails %s to the inspector alone, with its own template',
    async (_stage, command, subject, kind) => {
      const { logs, email, useCase } = setup();

      await useCase.execute(command);

      expect(email.sent.map((m) => m.to)).toEqual([INSPECTOR_RECIPIENT.email]);
      expect(email.sent[0].subject).toBe(`[AgriContract] ${subject}`);
      expect(
        logs.get({
          eventId: command.eventId,
          recipientEmail: INSPECTOR_RECIPIENT.email,
          notificationType: kind.notificationType,
        }),
      ).toMatchObject({
        status: NotificationStatus.SENT,
        eventType: 'notification.level1_5_inspector_assignment_requested',
        notificationType: kind.notificationType,
        templateVersion: kind.templateVersion,
        recipientUserId: INSPECTOR_RECIPIENT.userId,
      });
    },
  );

  it('the assignment and its cancellation are two emails', async () => {
    const { email, useCase } = setup();

    await useCase.execute(inspectorCommand());
    await useCase.execute(CANCELLED);

    expect(email.sent).toHaveLength(2);
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(inspectorCommand());
    await useCase.execute(inspectorCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(INSPECTOR_RECIPIENT.email);

    await expect(useCase.execute(inspectorCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(inspectorCommand());
    expect(email.sent).toHaveLength(1);
  });
});

describe('level15InspectorAssignmentBody', () => {
  const DETAILS = [
    `Contract:   ${INSPECTOR_CONTRACT_ID}`,
    `Milestone:  ${INSPECTOR_MILESTONE_ID}`,
    `Commission: ${INSPECTOR_COMMISSION_ID}`,
  ];

  it('ASSIGNED (level1-5-inspector-assigned/v1): the case, the due date, and not to start before the free cancellation deadline', () => {
    expect(level15InspectorAssignmentBody(inspectorCommand())).toBe(
      [
        'You have been assigned a Level 1.5 inspection of a disputed milestone.',
        '',
        ...DETAILS,
        '',
        'Report due:',
        '2026-11-25 17:00:00 Vietnam time (2026-11-25T10:00:00.000Z UTC)',
        '',
        'The case may still be cancelled until:',
        '2026-11-21 09:00:00 Vietnam time (2026-11-21T02:00:00.000Z UTC)',
        'Do not start the inspection before then. If the parties end the',
        'dispute first, the case is cancelled and you will be told by email.',
        '',
      ].join('\n'),
    );
  });

  it('CANCELLED (level1-5-inspector-cancelled/v1): the case is closed, no visit, no report', () => {
    expect(level15InspectorAssignmentBody(CANCELLED)).toBe(
      [
        'The Level 1.5 inspection you were assigned has been cancelled: the',
        'parties ended the milestone dispute.',
        '',
        'The case is closed. No visit and no report are needed.',
        '',
        ...DETAILS,
        '',
      ].join('\n'),
    );
  });

  it.each(['ASSIGNED', 'CANCELLED'] as const)(
    'keeps every line of %s within 76 characters',
    (stage) => {
      const body = level15InspectorAssignmentBody({
        ...inspectorCommand(),
        stage,
        reportDueAt: '2026-12-31T23:59:59.999Z',
        freeCancelUntil: '2026-12-31T23:59:59.999Z',
      });
      for (const line of body.split('\n')) {
        expect(line.length).toBeLessThanOrEqual(76);
      }
    },
  );
});
