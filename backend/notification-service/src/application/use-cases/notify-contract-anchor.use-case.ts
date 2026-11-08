import type { ContractAnchorRequestedCommand } from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import type { NotificationSender } from '../notification-sender';

/*
 * `notification.contract_anchor_requested` -> the signed-contract email
 * (design §4). This is the structured-snapshot mail, not the final document:
 * the PDF is `notification.contract_document_ready_requested` (§3.3). So the
 * body carries the terms as the canonical JSON the signature covers, which the
 * parties keep as their own copy of what they signed.
 *
 * The snapshot is rendered whole and never field by field: the command
 * declares it as a plain object with no required property, so a summary line
 * would read a field the contract does not promise, and the hash below is only
 * evidence if the reader can see every byte it covers.
 */

const KIND = COMMAND_KINDS.contractAnchor;

export class NotifyContractAnchorUseCase {
  constructor(private readonly sender: NotificationSender) {}

  /**
   * One email per recipient, each claimed and recorded on its own
   * (`recipient_user_id` included, so the row names the account as well as the
   * address). Sent in the command's recipient order, awaited one at a time: a
   * failure on the first recipient leaves the rest unsent and rethrows, and
   * the redelivery skips whoever is already SENT.
   */
  async execute(command: ContractAnchorRequestedCommand): Promise<void> {
    const subject = `[AgriContract] Signed contract ${command.contractId}`;
    const body = anchorBody(command);

    for (const recipient of command.recipients) {
      await this.sender.send(
        command.eventId,
        KIND,
        recipient.email,
        subject,
        body,
        recipient.userId,
      );
    }
  }
}

/**
 * The email text of `contract-anchor/v1`. Changing it is a new template
 * version: rows already written name the text they sent.
 */
export function anchorBody(command: ContractAnchorRequestedCommand): string {
  return [
    `Contract ${command.contractId} was signed at ${command.signedAt}.`,
    '',
    `Signed content hash (SHA-256): ${command.signedContentHash}`,
    '',
    'That hash covers the signed terms below, exactly as they are printed here.',
    'Keep this email: it lets you show later that the terms have not changed.',
    '',
    'Signed terms:',
    JSON.stringify(command.contractTermsSnapshot, null, 2),
    '',
  ].join('\n');
}
