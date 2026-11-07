import type { NotificationKind } from '../domain/notification-log';

/*
 * The email kind of each Phase 1 event (#115): eventType is the routing key of
 * its queue, notificationType names the one Java template the event sends.
 * Each Phase 1 event sends one template, the same to all its recipients, so
 * the dedup key (event, recipient, type) keeps the Phase 1 behaviour of one
 * email per event and recipient. These values are stored in notification_logs:
 * do not rename them.
 */
export const PHASE1_KINDS = {
  contractSigned: {
    eventType: 'contract.signed',
    notificationType: 'CONTRACT_SIGNED',
  },
  contractDelivered: {
    eventType: 'contract.delivered',
    notificationType: 'CONTRACT_DELIVERED',
  },
  contractCancelled: {
    eventType: 'contract.cancelled',
    notificationType: 'CONTRACT_CANCELLED',
  },
  contractDisputed: {
    eventType: 'contract.disputed',
    notificationType: 'CONTRACT_DISPUTED',
  },
  escrowLocked: {
    eventType: 'escrow.locked',
    notificationType: 'ESCROW_LOCKED',
  },
  escrowReleased: {
    eventType: 'escrow.released',
    notificationType: 'ESCROW_RELEASED',
  },
  escrowPenalized: {
    eventType: 'escrow.penalized',
    notificationType: 'ESCROW_PENALIZED',
  },
  categoryApproved: {
    eventType: 'category.approved',
    notificationType: 'CATEGORY_APPROVED',
  },
  categoryRejected: {
    eventType: 'category.rejected',
    notificationType: 'CATEGORY_REJECTED',
  },
} as const satisfies Record<string, NotificationKind>;
