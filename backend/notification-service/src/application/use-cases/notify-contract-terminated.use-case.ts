import type {
  ContractTerminatedRequestedCommand,
  NotificationRecipient,
  TerminationType,
} from '../commands';
import type { NotificationKind } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';

/*
 * `notification.contract_terminated_requested` -> one email per recipient,
 * with the template of its terminationType. Only WITHDRAW_OFFER has one today
 * (D43, #397); MUTUAL_TERMINATION, MUTUAL_REPLACEMENT, TERMINATION_FOR_BREACH,
 * TERMINATION_FOR_FORCE_MAJEURE and ACTIVATION_FAILURE come with #278, each as
 * a new entry of TEMPLATES with its own kind (its own notification type, so
 * the dedup key keeps them apart).
 *
 * A command whose type has no template must not be acked as if mailed:
 * unsupportedTermination names why, and the route dead-letters it.
 */

interface TerminationTemplate {
  kind: NotificationKind;
  /** Why this command cannot be rendered by this template, or null. */
  unsupported(command: ContractTerminatedRequestedCommand): string | null;
  subject(command: ContractTerminatedRequestedCommand): string;
  body(
    command: ContractTerminatedRequestedCommand,
    recipient: NotificationRecipient,
  ): string;
}

const PARTY_NAME = { BUYER: 'buyer', SELLER: 'seller' } as const;

/*
 * WITHDRAW_OFFER (D43, #233): a party ended the offer before the second
 * signature. Nothing was locked, so no money moves and nobody is penalised;
 * product-service returns the reserved quantity to the listing
 * (contract.withdrawn). contract-service addresses only the other party.
 */
const OFFER_WITHDRAWN: TerminationTemplate = {
  kind: COMMAND_KINDS.offerWithdrawn,
  unsupported: (command) =>
    command.requestedBy === 'SYSTEM'
      ? 'WITHDRAW_OFFER requested by SYSTEM has no email template: only a party withdraws an offer'
      : null,
  subject: (command) =>
    `[AgriContract] Offer withdrawn for contract ${command.contractId}`,
  body: (command) => offerWithdrawnBody(command),
};

const TEMPLATES: Partial<Record<TerminationType, TerminationTemplate>> = {
  WITHDRAW_OFFER: OFFER_WITHDRAWN,
};

/**
 * Why this command cannot be mailed yet, or null when it can: a termination
 * type without a template (#278), or a case its template does not cover.
 * The reason quotes only enum values, so it is safe to log.
 */
export function unsupportedTermination(
  command: ContractTerminatedRequestedCommand,
): string | null {
  const template = TEMPLATES[command.terminationType];
  if (!template) {
    return `terminationType ${command.terminationType} has no email template yet (#278)`;
  }
  return template.unsupported(command);
}

export class NotifyContractTerminatedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email per recipient, each claimed and recorded on its own, in the
   * command's order; a failure rethrows and the redelivery skips whoever is
   * already SENT (as NotifyContractAnchorUseCase). Throws for a command
   * unsupportedTermination refuses: the caller checks that first.
   */
  async execute(command: ContractTerminatedRequestedCommand): Promise<void> {
    const reason = unsupportedTermination(command);
    const template = TEMPLATES[command.terminationType];
    if (reason !== null || !template) {
      throw new Error(`Event ${command.eventId}: ${reason}`);
    }

    const subject = template.subject(command);
    for (const recipient of command.recipients) {
      await this.sender.send(
        command.eventId,
        template.kind,
        recipient.email,
        subject,
        template.body(command, recipient),
        recipient.userId,
      );
    }
  }
}

/**
 * The email text of `offer-withdrawn/v1`. Changing it is a new template
 * version: rows already written name the text they sent. Lines stay within 76
 * characters, so nodemailer sends it as 7bit.
 */
export function offerWithdrawnBody(
  command: ContractTerminatedRequestedCommand,
): string {
  if (command.requestedBy === 'SYSTEM') {
    throw new Error('offer-withdrawn/v1 needs the party that withdrew');
  }
  const party = PARTY_NAME[command.requestedBy];
  return [
    `The ${party} has withdrawn the offer for contract`,
    `${command.contractId}.`,
    '',
    'The offer was withdrawn before both parties had signed it, so the',
    'contract can no longer be signed and will not come into force.',
    '',
    'Nobody is penalised: no deposit had been locked, so no money moves.',
    'The reserved quantity goes back to the listing.',
    '',
  ].join('\n');
}
