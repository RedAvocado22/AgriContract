import type { NotificationRecipient } from './notification-recipient';

/** Which point of the assignment the command reports: the contract's `stage` enum. */
export const LEVEL15_INSPECTOR_STAGES = ['ASSIGNED', 'CANCELLED'] as const;
export type Level15InspectorStage = (typeof LEVEL15_INSPECTOR_STAGES)[number];

/**
 * `notification.level1_5_inspector_assignment_requested`
 * (notification-commands.yaml `level15InspectorAssignmentRequested`, D57),
 * published by inspection-service to the inspector it picked for a Level 1.5
 * inspection (inspection design §2.5).
 *
 * ASSIGNED once per assignment, in the transaction that creates it: the case,
 * the report due date, and the free cancellation deadline before which the
 * inspector should not start. CANCELLED once when the assignment ended
 * because the dispute ended (inspection.level1_5_cancelled). `eventId` is the
 * envelope's: the dedup key's event (design §5), so each stage is its own
 * email.
 */
export interface Level15InspectorAssignmentRequestedCommand {
  eventId: string;
  contractId: string;
  milestoneId: string;
  commissionId: string;
  /**
   * The contract's `recipients`, which holds exactly one entry, of role
   * INSPECTOR: the parser dead-letters anything else.
   */
  recipient: NotificationRecipient & { role: 'INSPECTOR' };
  reportDueAt: string;
  freeCancelUntil: string;
  stage: Level15InspectorStage;
}
