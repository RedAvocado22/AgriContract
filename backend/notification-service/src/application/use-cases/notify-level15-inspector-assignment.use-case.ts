import type {
  Level15InspectorAssignmentRequestedCommand,
  Level15InspectorStage,
} from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.level1_5_inspector_assignment_requested` -> the assigned
 * inspector (design §4, D57; inspection design §2.5). inspection-service
 * picked this inspector at random for a Level 1.5 inspection of a disputed
 * milestone.
 *
 * ASSIGNED gives the case, the report due date and the free cancellation
 * deadline: until then the parties may still end the dispute (concession or
 * agreed settlement) and the case is cancelled, so the inspector should not
 * start before it. CANCELLED says the case is closed: no visit, no report.
 */

type Kind = (typeof COMMAND_KINDS)[keyof typeof COMMAND_KINDS];

const KINDS: Record<Level15InspectorStage, Kind> = {
  ASSIGNED: COMMAND_KINDS.level15InspectorAssigned,
  CANCELLED: COMMAND_KINDS.level15InspectorCancelled,
};

const SUBJECTS: Record<Level15InspectorStage, string> = {
  ASSIGNED: '[AgriContract] You have been assigned a Level 1.5 inspection',
  CANCELLED: '[AgriContract] Your Level 1.5 inspection was cancelled',
};

export class NotifyLevel15InspectorAssignmentUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email of the stage's kind to the one inspector, claimed and recorded
   * as every other; a failure rethrows and the redelivery sends it unless it
   * is already SENT.
   */
  async execute(
    command: Level15InspectorAssignmentRequestedCommand,
  ): Promise<void> {
    await this.sender.send(
      command.eventId,
      KINDS[command.stage],
      command.recipient.email,
      SUBJECTS[command.stage],
      level15InspectorAssignmentBody(command),
      command.recipient.userId,
    );
  }
}

/**
 * The email text of `level1-5-inspector-assigned/v1` and
 * `level1-5-inspector-cancelled/v1`. Changing one is a new version of that
 * template: rows already written name the text they sent. Every line stays
 * within 76 characters, so the email goes out as 7bit and the instants read
 * unbroken.
 */
export function level15InspectorAssignmentBody(
  command: Level15InspectorAssignmentRequestedCommand,
): string {
  const details = [
    `Contract:   ${command.contractId}`,
    `Milestone:  ${command.milestoneId}`,
    `Commission: ${command.commissionId}`,
  ];
  let lines: string[];

  switch (command.stage) {
    case 'ASSIGNED':
      lines = [
        'You have been assigned a Level 1.5 inspection of a disputed milestone.',
        '',
        ...details,
        '',
        'Report due:',
        instant(command.reportDueAt),
        '',
        'The case may still be cancelled until:',
        instant(command.freeCancelUntil),
        'Do not start the inspection before then. If the parties end the',
        'dispute first, the case is cancelled and you will be told by email.',
      ];
      break;
    case 'CANCELLED':
      lines = [
        'The Level 1.5 inspection you were assigned has been cancelled: the',
        'parties ended the milestone dispute.',
        '',
        'The case is closed. No visit and no report are needed.',
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
