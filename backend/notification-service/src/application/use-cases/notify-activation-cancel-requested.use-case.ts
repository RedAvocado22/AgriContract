import type {
  ActivationCancelRequestedCommand,
  NotificationRecipient,
} from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.activation_cancel_requested` -> "the other party asked to
 * cancel" (design §4, D42). The contract was signed but never became ACTIVE
 * because a deposit lock failed. One party has now recorded a cancel request;
 * this tells the other party what that means so that silence is a choice:
 * - if they request the cancel too, the refund of the deposits starts at once;
 * - if they do nothing, the single request is enough from
 *   `oneSidedCancelAllowedFrom`, and contract-service then cancels on its own
 *   unless the requester withdraws first (DELETE .../activation/cancel).
 * Nobody breached, so nobody is penalised (D42: no deposit is forfeited).
 * If the deposits get locked first (an Operator retry), the contract becomes
 * ACTIVE and every cancel request lapses: the email says so, not promising
 * the cancel.
 *
 * A recipient who is not the other party (an OPERATOR, should the publisher
 * add one) gets the same facts without the "you can" lines.
 */

const KIND = COMMAND_KINDS.activationCancelRequested;

export class NotifyActivationCancelRequestedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email per recipient, each claimed and recorded on its own, in the
   * command's order; a failure rethrows and the redelivery skips whoever is
   * already SENT (as NotifyContractAnchorUseCase).
   */
  async execute(command: ActivationCancelRequestedCommand): Promise<void> {
    const subject = `[AgriContract] Cancel requested for contract ${command.contractId}`;

    for (const recipient of command.recipients) {
      await this.sender.send(
        command.eventId,
        KIND,
        recipient.email,
        subject,
        activationCancelBody(command, recipient),
        recipient.userId,
      );
    }
  }
}

const PARTY_NAME = { BUYER: 'buyer', SELLER: 'seller' } as const;

/**
 * The email text of `activation-cancel-requested/v1`. Changing it is a new
 * template version: rows already written name the text they sent. Lines stay
 * within 76 characters, so nodemailer sends it as 7bit.
 */
export function activationCancelBody(
  command: ActivationCancelRequestedCommand,
  recipient: NotificationRecipient,
): string {
  const requester = PARTY_NAME[command.requestedByRole];
  const isOtherParty =
    recipient.role !== command.requestedByRole &&
    (recipient.role === 'BUYER' || recipient.role === 'SELLER');
  const from = new Date(command.oneSidedCancelAllowedFrom);
  const fromLine = `${vietnamTime(from)} Vietnam time (${from.toISOString()} UTC)`;

  const lines = [
    `The ${requester} has asked to cancel contract`,
    `${command.contractId}.`,
    '',
    'The contract was signed but never became active, because the deposits',
    'could not be locked. Nobody breached it, so nobody is penalised.',
    '',
  ];
  if (isOtherParty) {
    lines.push(
      'If you also want to cancel, request the cancel in AgriContract: once',
      'both parties have asked, the refund of the deposits starts at once.',
      '',
      `If you do nothing, the ${requester}'s request alone is enough from:`,
      fromLine,
      `Unless the ${requester} withdraws the request by then, the contract is`,
      'then cancelled and the deposits returned without anything more from you.',
    );
  } else {
    lines.push(
      `If the other party does not also ask, the ${requester}'s request alone`,
      'is enough from:',
      fromLine,
    );
  }
  // D42: an Operator retry that locks the deposits first makes the contract
  // ACTIVE and every cancel request lapses, so the cancel is not promised.
  lines.push(
    '',
    'If the deposits are locked before then, the contract becomes active as',
    'signed and every cancel request lapses.',
    '',
  );
  return lines.join('\n');
}
