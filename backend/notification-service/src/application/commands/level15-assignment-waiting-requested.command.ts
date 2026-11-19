/** Why no inspector could be assigned: the contract's `reason` enum. */
export const LEVEL15_WAITING_REASONS = ['NO_ELIGIBLE', 'ALL_EXCLUDED'] as const;
export type Level15WaitingReason = (typeof LEVEL15_WAITING_REASONS)[number];

/** Which point of the wait the command reports: the contract's `stage` enum. */
export const LEVEL15_WAITING_STAGES = [
  'WAITING_STARTED',
  'STILL_WAITING',
  'ASSIGNED',
  'CANCELLED',
] as const;
export type Level15WaitingStage = (typeof LEVEL15_WAITING_STAGES)[number];

/**
 * `notification.level1_5_assignment_waiting_requested` (notification-commands.yaml
 * `level15AssignmentWaitingRequested`, D49), published by inspection-service
 * when a Level 1.5 inspection request finds no inspector to assign
 * (inspection design §2.5). A staff-only alert: the command names no
 * recipient, and the email goes to this service's `notification.operatorEmail`.
 *
 * One command per stage: WAITING_STARTED once when the wait begins,
 * STILL_WAITING at most once per business day while it lasts, ASSIGNED once
 * an inspector was found, CANCELLED once when the request was cancelled
 * because the dispute ended while it was still waiting (D57). `eventId` is the envelope's: the dedup key's event
 * (design §5), so each stage is its own email.
 */
export interface Level15AssignmentWaitingRequestedCommand {
  eventId: string;
  contractId: string;
  milestoneId: string;
  reason: Level15WaitingReason;
  /** When the wait began; the same instant in every command of one wait. */
  waitingSince: string;
  stage: Level15WaitingStage;
}
