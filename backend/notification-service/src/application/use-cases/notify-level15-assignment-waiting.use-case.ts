import type {
  Level15AssignmentWaitingRequestedCommand,
  Level15WaitingReason,
  Level15WaitingStage,
} from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { OperatorEmailNotConfiguredError } from '../notification-errors';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.level1_5_assignment_waiting_requested` -> the Operator (design
 * §4, D49; inspection design §2.5). A Level 1.5 inspection request found no
 * inspector to assign: inspection-service keeps it waiting and retries on its
 * own, and tells the Operator when the wait starts, at most once per business
 * day while it lasts, and when an inspector was finally assigned.
 *
 * The command names no recipient: the email goes to `notification.operatorEmail`.
 * It names no inspector either, and neither does the email. What the Operator
 * can do is ask an Admin for more INSPECTOR accounts; picking an inspector by
 * hand is not a way out (assignment stays random, inspection design §2.5).
 */

type Kind = (typeof COMMAND_KINDS)[keyof typeof COMMAND_KINDS];

const KINDS: Record<Level15WaitingStage, Kind> = {
  WAITING_STARTED: COMMAND_KINDS.level15WaitingStarted,
  STILL_WAITING: COMMAND_KINDS.level15StillWaiting,
  ASSIGNED: COMMAND_KINDS.level15AssignedAfterWait,
};

const SUBJECTS: Record<Level15WaitingStage, string> = {
  WAITING_STARTED:
    '[AgriContract] A Level 1.5 inspection is waiting for an inspector',
  STILL_WAITING:
    '[AgriContract] A Level 1.5 inspection is still waiting for an inspector',
  ASSIGNED:
    '[AgriContract] A waiting Level 1.5 inspection now has an inspector',
};

const REASONS: Record<Level15WaitingReason, string[]> = {
  NO_ELIGIBLE: ['No inspector is eligible for it.'],
  ALL_EXCLUDED: [
    'Every eligible inspector has already been assigned this milestone',
    'once, so none of them can take it again.',
  ],
};

export class NotifyLevel15AssignmentWaitingUseCase {
  /**
   * `operatorEmail` is null when OPERATOR_EMAIL is not set: every command
   * then fails with OperatorEmailNotConfiguredError instead of being acked
   * unsent.
   */
  constructor(
    private readonly sender: NotificationSender,
    private readonly operatorEmail: string | null,
  ) {}

  /**
   * One email of the stage's kind, claimed and recorded as every other; a
   * failure rethrows and the redelivery sends it unless it is already SENT.
   * There is no platform user behind the address, so no recipient id.
   */
  async execute(
    command: Level15AssignmentWaitingRequestedCommand,
  ): Promise<void> {
    if (this.operatorEmail === null) {
      throw new OperatorEmailNotConfiguredError(command.eventId);
    }
    await this.sender.send(
      command.eventId,
      KINDS[command.stage],
      this.operatorEmail,
      SUBJECTS[command.stage],
      level15AssignmentWaitingBody(command),
    );
  }
}

const WHAT_TO_DO = [
  'What you can do:',
  '- Ask an Admin to create more INSPECTOR accounts. The request is assigned',
  '  automatically, at random, as soon as an inspector is eligible.',
  '- Do not pick an inspector by hand: there is no manual assignment.',
];

/**
 * The email text of the three templates: `level1-5-waiting-started/v1`,
 * `level1-5-still-waiting/v1` and `level1-5-assigned-after-wait/v1`. Changing
 * one is a new version of that template: rows already written name the text
 * they sent. Every line stays within 76 characters, so the email goes out
 * as 7bit and the instant reads unbroken.
 */
export function level15AssignmentWaitingBody(
  command: Level15AssignmentWaitingRequestedCommand,
): string {
  const details = [
    `Contract:  ${command.contractId}`,
    `Milestone: ${command.milestoneId}`,
    'Waiting since:',
    instant(command.waitingSince),
  ];
  let lines: string[];

  switch (command.stage) {
    case 'WAITING_STARTED':
      lines = [
        'A Level 1.5 inspection request could not be assigned to an inspector.',
        ...REASONS[command.reason],
        '',
        'The request is waiting, and inspection-service keeps trying to assign',
        'it. You will be reminded at most once per business day while it',
        'waits, and told when it is assigned.',
        '',
        ...details,
        '',
        ...WHAT_TO_DO,
      ];
      break;
    case 'STILL_WAITING':
      lines = [
        'A Level 1.5 inspection request is still waiting for an inspector.',
        ...REASONS[command.reason],
        '',
        ...details,
        '',
        ...WHAT_TO_DO,
      ];
      break;
    case 'ASSIGNED':
      lines = [
        'A Level 1.5 inspection request that was waiting for an inspector has',
        'now been assigned to one. Nothing more is needed from you.',
        '',
        'The report deadline counts from the assignment, not from the start',
        'of the wait.',
        '',
        ...details,
      ];
      break;
  }

  lines.push('');
  return lines.join('\n');
}

function instant(value: string): string {
  const date = new Date(value);
  return `${vietnamTime(date)} Vietnam time (${date.toISOString()} UTC)`;
}
