import type { NotificationKind } from '../domain/notification-log';

/*
 * The email kind of each Phase 1 event (#115): eventType is the routing key of
 * its queue, notificationType names the one Java template the event sends, and
 * templateVersion names that template's version. Each Phase 1 event sends one
 * template, the same to all its recipients, so the dedup key (event, recipient,
 * type) keeps the Phase 1 behaviour of one email per event and recipient.
 *
 * The `/p1` versions are the subjects and bodies copied verbatim from the Java
 * ProcessNotificationUseCaseImpl and frozen by test/fixtures/java-templates.json:
 * changing one of those texts is a new version, not an edit of this value.
 * These values are stored in notification_logs: do not rename them.
 *
 * ESCROW_LOCKED, _RELEASED and _PENALIZED went with their queues (#421):
 * escrow-service publishes none of those events any more. Rows already stored
 * keep those values, so the names must not be reused for anything else.
 */
export const PHASE1_KINDS = {
  categoryApproved: {
    eventType: 'category.approved',
    notificationType: 'CATEGORY_APPROVED',
    templateVersion: 'category-approved/p1',
  },
  categoryRejected: {
    eventType: 'category.rejected',
    notificationType: 'CATEGORY_REJECTED',
    templateVersion: 'category-rejected/p1',
  },
} as const satisfies Record<string, NotificationKind>;
