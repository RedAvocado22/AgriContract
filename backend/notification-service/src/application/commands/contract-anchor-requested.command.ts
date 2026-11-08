import type { NotificationRecipient } from './notification-recipient';

/**
 * `notification.contract_anchor_requested` (notification-commands.yaml
 * `contractAnchorRequested`), published by contract-service in the verify-sign
 * transaction (#360): the signed-contract email to both parties.
 *
 * `eventId` is the envelope's, not a payload field: it is the dedup key's event
 * (design §5), so a redelivery of the same command sends nothing twice.
 */
export interface ContractAnchorRequestedCommand {
  eventId: string;
  contractId: string;
  recipients: NotificationRecipient[];
  /**
   * The canonical JSON of the terms the parties signed, as an object. The
   * contract declares it as a plain object with no required field, so the
   * template renders it whole instead of reading fields out of it: it is
   * evidence, and `signedContentHash` covers exactly these bytes.
   */
  contractTermsSnapshot: Record<string, unknown>;
  /** SHA-256 of the canonical terms, 64 lowercase hex characters. */
  signedContentHash: string;
  signedAt: string;
}
