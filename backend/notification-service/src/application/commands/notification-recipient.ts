/*
 * `payload.recipients` of every Phase 2 notification command
 * (contracts/events/notification-commands.yaml, `recipients`). The publisher
 * resolves the addresses before publishing; notification-service never calls
 * back to user-service for them (design §2.1).
 */

/** The roles a command may address, exactly the contract's `recipients.items.role` enum. */
export const RECIPIENT_ROLES = [
  'BUYER',
  'SELLER',
  'ADMIN',
  'OPERATOR',
  'ARBITRATOR',
  'INSPECTOR',
  'SOFTWARE_BUYER',
] as const;

export type RecipientRole = (typeof RECIPIENT_ROLES)[number];

export interface NotificationRecipient {
  /**
   * Platform id, stored in `notification_logs.recipient_user_id`. Nullable by
   * contract: a Software Buyer distribution contact has no account (design
   * §3.1), so the address alone identifies the recipient.
   */
  userId: string | null;
  email: string;
  role: RecipientRole;
}
