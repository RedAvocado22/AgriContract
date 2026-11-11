/** The KYC decision: the contract's `result` enum. */
export const KYC_RESULTS = ['VERIFIED', 'REJECTED'] as const;
export type KycResult = (typeof KYC_RESULTS)[number];

/**
 * `notification.user_kyc_result_requested` (notification-commands.yaml
 * `userKycResultRequested`), published by user-service when an Admin verifies
 * or rejects an organisation's KYC (OutboxKycResultPublisher). It goes to that
 * user alone: unlike the contract commands there is no `payload.recipients`,
 * the one address is `recipientEmail`.
 *
 * `eventId` is the envelope's: the dedup key's event (design §5). Every
 * decision is a new command, so a user rejected, resubmitted and rejected
 * again is mailed twice.
 */
export interface UserKycResultRequestedCommand {
  eventId: string;
  userId: string;
  recipientEmail: string;
  organizationName: string;
  result: KycResult;
  /** Only sent for REJECTED (user-service requires one); absent otherwise. */
  reason: string | null;
  decidedAt: string;
}
