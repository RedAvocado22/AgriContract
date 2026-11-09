import type { NotificationKind } from '../domain/notification-log';

/*
 * The email kind of each Phase 2 notification command (design §4), the
 * counterpart of PHASE1_KINDS for the commands of
 * contracts/events/notification-commands.yaml.
 *
 * eventType is the command's `eventType` (its routing key too), notificationType
 * names the template semantically, and templateVersion names the exact text the
 * row recorded. Unlike the Phase 1 `/p1` versions, these are not frozen Java
 * texts: a changed subject or body is a new `/vN`, never an edit of this value,
 * because `template_version` is the evidence of what was sent (design §3.1).
 *
 * One command may later send several templates to the same recipient (a
 * `statusType` per template, design §4); the dedup key (event, recipient, type)
 * keeps those apart, so each gets its own entry here rather than a shared one.
 *
 * These values are stored in notification_logs: do not rename them.
 */
export const COMMAND_KINDS = {
  contractAnchor: {
    eventType: 'notification.contract_anchor_requested',
    notificationType: 'CONTRACT_ANCHOR',
    templateVersion: 'contract-anchor/v1',
  },
  activationCancelRequested: {
    eventType: 'notification.activation_cancel_requested',
    notificationType: 'ACTIVATION_CANCEL_REQUESTED',
    templateVersion: 'activation-cancel-requested/v1',
  },
} as const satisfies Record<string, NotificationKind>;
