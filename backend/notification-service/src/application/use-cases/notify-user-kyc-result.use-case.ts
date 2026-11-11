import type { UserKycResultRequestedCommand } from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.user_kyc_result_requested` -> "the KYC decision on your
 * organisation" (design §4). An Admin verified or rejected the documents a
 * user submitted for their organisation; the user is told which, and for a
 * rejection the reason the Admin gave, which user-service requires.
 *
 * The email promises no next step: user-service can move a rejected profile
 * back to PENDING (resubmit), but exposes no endpoint for it yet, so telling
 * the user to resubmit would send them to a door that is not there.
 */

const KIND = COMMAND_KINDS.userKycResult;

const SUBJECTS = {
  VERIFIED: '[AgriContract] Your organisation is verified',
  REJECTED: '[AgriContract] Your organisation was not verified',
} as const;

export class NotifyUserKycResultUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email, claimed and recorded as every other; a failure rethrows and
   * the redelivery sends it unless it is already SENT.
   */
  async execute(command: UserKycResultRequestedCommand): Promise<void> {
    await this.sender.send(
      command.eventId,
      KIND,
      command.recipientEmail,
      SUBJECTS[command.result],
      userKycResultBody(command),
      command.userId,
    );
  }
}

/**
 * The email text of `user-kyc-result/v1`. Changing it is a new template
 * version: rows already written name the text they sent. The fixed lines stay
 * within 76 characters; the organisation name and the reason are the user's
 * and the Admin's own text, quoted on lines of their own as sent (a long or
 * non-ASCII one makes nodemailer pick quoted-printable, which is fine).
 */
export function userKycResultBody(
  command: UserKycResultRequestedCommand,
): string {
  const decidedAt = new Date(command.decidedAt);
  const organisation =
    command.organizationName.trim() === ''
      ? '(no organisation name on record)'
      : command.organizationName;

  const lines =
    command.result === 'VERIFIED'
      ? [
          'The KYC verification of your organisation on AgriContract is done.',
          'This organisation has been verified:',
          '',
          `  ${organisation}`,
          '',
        ]
      : [
          'The KYC verification of your organisation on AgriContract is done.',
          'This organisation has not been verified:',
          '',
          `  ${organisation}`,
          '',
          ...reasonLines(command.reason),
          '',
        ];

  lines.push(
    'Decided at:',
    `${vietnamTime(decidedAt)} Vietnam time (${decidedAt.toISOString()} UTC)`,
    '',
  );
  return lines.join('\n');
}

/** The Admin's reason, each of its lines indented; CRLF read as one break. */
function reasonLines(reason: string | null): string[] {
  if (reason === null || reason.trim() === '') {
    return ['No reason was given.'];
  }
  return [
    'Reason given:',
    ...reason
      .trim()
      .split(/\r\n|\r|\n/)
      .map((line) => `  ${line}`.trimEnd()),
  ];
}
