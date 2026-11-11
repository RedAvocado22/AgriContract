import type { UserLockChangedRequestedCommand } from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.user_lock_changed_requested` -> "your account was locked /
 * unlocked" (design §4). reputation-service decides a lock after a breach
 * attributed to the user, or lifts one early; user-service applies it and
 * asks for this email. While locked, a user cannot create listings or offers,
 * nor sign contracts (user-service design §3; product-service and
 * contract-service enforce it).
 *
 * Three cases, from `action` and `effectiveUntil` (the lock stored after the
 * change, never the incoming value):
 * - LOCKED: locked until `effectiveUntil`. A shorter new lock does not
 *   shorten a longer one, so the date may be later than the new lock's.
 * - UNLOCKED with null: no lock is left.
 * - UNLOCKED with a date: one lock was lifted, but another still applies
 *   until then. Saying "unlocked" there would be false.
 * `reasonCode` is reputation-service's code, quoted as sent: there is no
 * agreed wording for its values, so the email does not translate them.
 */

const KIND = COMMAND_KINDS.userLockChanged;

type LockCase = 'LOCKED' | 'UNLOCKED' | 'STILL_LOCKED';

const SUBJECTS: Record<LockCase, string> = {
  LOCKED: '[AgriContract] Your account is locked',
  UNLOCKED: '[AgriContract] Your account is no longer locked',
  STILL_LOCKED: '[AgriContract] A lock on your account was lifted',
};

const lockCase = (command: UserLockChangedRequestedCommand): LockCase => {
  if (command.action === 'LOCKED') return 'LOCKED';
  return command.effectiveUntil === null ? 'UNLOCKED' : 'STILL_LOCKED';
};

export class NotifyUserLockChangedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email, claimed and recorded as every other; a failure rethrows and
   * the redelivery sends it unless it is already SENT.
   */
  async execute(command: UserLockChangedRequestedCommand): Promise<void> {
    await this.sender.send(
      command.eventId,
      KIND,
      command.recipientEmail,
      SUBJECTS[lockCase(command)],
      userLockChangedBody(command),
      command.userId,
    );
  }
}

const BLOCKED = [
  'While it is locked, you cannot create listings or offers, nor sign',
  'contracts.',
];

/**
 * The email text of `user-lock-changed/v1`. Changing it is a new template
 * version: rows already written name the text they sent. The fixed lines
 * stay within 76 characters; the reason code is quoted as sent.
 */
export function userLockChangedBody(
  command: UserLockChangedRequestedCommand,
): string {
  const until =
    command.effectiveUntil === null ? null : instant(command.effectiveUntil);
  let lines: string[];

  switch (lockCase(command)) {
    case 'LOCKED':
      lines =
        until === null
          ? ['Your AgriContract account has been locked.', '', ...BLOCKED]
          : [
              'Your AgriContract account has been locked until:',
              until,
              '',
              ...BLOCKED,
            ];
      break;
    case 'UNLOCKED':
      lines = [
        'The lock on your AgriContract account has been lifted, and no other',
        'lock applies: the lock no longer stops you from creating listings or',
        'offers, nor from signing contracts.',
      ];
      break;
    case 'STILL_LOCKED':
      lines = [
        'A lock on your AgriContract account has been lifted, but another lock',
        'still applies, so your account stays locked until:',
        until as string,
        '',
        ...BLOCKED,
      ];
      break;
  }

  lines.push(
    '',
    `Reason code: ${command.reasonCode.trim() === '' ? '(none given)' : command.reasonCode}`,
    '',
  );
  return lines.join('\n');
}

function instant(value: string): string {
  const date = new Date(value);
  return `${vietnamTime(date)} Vietnam time (${date.toISOString()} UTC)`;
}
