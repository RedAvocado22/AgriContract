import type {
  ContractActivationFailedRequestedCommand,
  FailedLeg,
  NotificationRecipient,
} from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';
import { vietnamTime } from '../vietnam-time';

/*
 * `notification.contract_activation_failed_requested` -> "the deposits could
 * not be locked" (design §4, milestone-escrow §3.1, D42). Both parties signed,
 * but escrow-service ran out of lock retries for at least one deposit, so the
 * contract stays SIGNED and never became ACTIVE. The email says:
 * - which deposit failed, and the state of each one;
 * - that nobody breached, so nobody is penalised (no deposit forfeited, no
 *   reputation change: the failure is the platform's, D42);
 * - the next steps the command lists: an Operator may retry the lock (if it
 *   succeeds the contract becomes ACTIVE and every cancel request lapses),
 *   and each party may request the cancel; once both asked the refund starts
 *   at once, and from `oneSidedCancelAllowedFrom` one request is enough.
 *
 * A recipient who is not a party (an OPERATOR, should the publisher add one)
 * gets the same facts, with "each party can" instead of "you can".
 */

const KIND = COMMAND_KINDS.contractActivationFailed;

export class NotifyContractActivationFailedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email per recipient, each claimed and recorded on its own, in the
   * command's order; a failure rethrows and the redelivery skips whoever is
   * already SENT (as NotifyContractAnchorUseCase).
   */
  async execute(
    command: ContractActivationFailedRequestedCommand,
  ): Promise<void> {
    const subject = `[AgriContract] Deposits not locked for contract ${command.contractId}`;

    for (const recipient of command.recipients) {
      await this.sender.send(
        command.eventId,
        KIND,
        recipient.email,
        subject,
        activationFailedBody(command, recipient),
        recipient.userId,
      );
    }
  }
}

const FAILED_DEPOSIT: Record<FailedLeg, string> = {
  BUYER: "the buyer's deposit",
  SELLER: "the seller's deposit",
  BOTH: 'both deposits',
};

/** escrow-service's leg states (OutboxDepositEventPublisher.depositLockFailed). */
const DEPOSIT_STATE = new Map<string, string>([
  ['DEPOSIT_LOCKED', 'locked'],
  ['LOCK_FAILED', 'not locked, the lock failed'],
  ['NOT_REQUIRED', 'none, this contract does not require it'],
]);

/** A state code this template has no words for yet, printed as sent. */
const STATE_CODE = /^[A-Z][A-Z0-9_]{0,39}$/;

/**
 * The contract types the state as a plain string, so a value this template
 * does not know is shown as its code when it looks like one, never as free
 * text: the line must stay short and 7bit.
 */
function depositState(state: string): string {
  const known = DEPOSIT_STATE.get(state);
  if (known !== undefined) return known;
  return STATE_CODE.test(state) ? `reported as ${state}` : 'not reported';
}

/**
 * The email text of `contract-activation-failed/v1`. Changing it is a new
 * template version: rows already written name the text they sent. Lines stay
 * within 76 characters, so nodemailer sends it as 7bit.
 */
export function activationFailedBody(
  command: ContractActivationFailedRequestedCommand,
  recipient: NotificationRecipient,
): string {
  const isParty = recipient.role === 'BUYER' || recipient.role === 'SELLER';
  const from = new Date(command.oneSidedCancelAllowedFrom);
  const fromLine = `${vietnamTime(from)} Vietnam time (${from.toISOString()} UTC)`;

  const lines = [
    'The deposits of contract',
    `${command.contractId}`,
    'could not be locked. The contract is signed but has not become active.',
    '',
    `Not locked: ${FAILED_DEPOSIT[command.failedLeg]}.`,
    `Buyer deposit: ${depositState(command.buyerDepositState)}.`,
    `Seller deposit: ${depositState(command.sellerDepositState)}.`,
    '',
    'Nobody breached the contract, so nobody is penalised: no deposit is',
    'forfeited and no reputation score changes.',
    '',
  ];

  const retry = command.nextActions.includes('RETRY_DEPOSIT_LOCK');
  const cancel = command.nextActions.includes('REQUEST_ACTIVATION_CANCEL');
  if (retry || cancel) lines.push('What can happen next:', '');
  if (retry) {
    lines.push(
      '- An AgriContract operator may retry locking the deposits. If they',
      '  are locked, the contract becomes active as signed and every cancel',
      '  request lapses.',
      '',
    );
  }
  if (cancel) {
    lines.push(
      isParty
        ? '- You can request to cancel the contract in AgriContract. Once both'
        : '- Each party can request to cancel the contract. Once both',
      '  parties have asked, the refund of any locked deposit starts at once.',
      "  From the time below, one party's request alone is enough:",
      `  ${fromLine}`,
      '',
    );
  }
  return lines.join('\n');
}
