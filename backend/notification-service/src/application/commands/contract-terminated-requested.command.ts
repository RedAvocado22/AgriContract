import type { NotificationRecipient } from './notification-recipient';

/*
 * `notification.contract_terminated_requested` (notification-commands.yaml
 * `contractTerminatedRequested`), published by contract-service when a contract
 * ends. Every termination type parses; which ones have an email template is the
 * use case's business (only WITHDRAW_OFFER today, D43; the others are #278).
 */

/** The contract's `terminationType` enum. */
export const TERMINATION_TYPES = [
  'WITHDRAW_OFFER',
  'MUTUAL_TERMINATION',
  'MUTUAL_REPLACEMENT',
  'TERMINATION_FOR_BREACH',
  'TERMINATION_FOR_FORCE_MAJEURE',
  'ACTIVATION_FAILURE',
] as const;
export type TerminationType = (typeof TERMINATION_TYPES)[number];

/** Who asked for the termination: the contract's `requestedBy` enum. */
export const TERMINATION_REQUESTERS = ['BUYER', 'SELLER', 'SYSTEM'] as const;
export type TerminationRequester = (typeof TERMINATION_REQUESTERS)[number];

/** A contract party: `finalBreachingRole` and `remedyLeg.role`. */
export const PARTY_ROLES = ['BUYER', 'SELLER'] as const;
export type PartyRole = (typeof PARTY_ROLES)[number];

/** The contract's `breachReasonCode` enum. */
export const BREACH_REASON_CODES = [
  'NON_DELIVERY',
  'SHORTFALL',
  'LATE_DELIVERY',
  'QUALITY_BELOW_COMMITTED',
  'SIDE_SELLING',
  'PRODUCTION_SHOCK_NON_FM',
  'FUNDING_FAILURE',
  'LATE_PAYMENT',
  'FAILURE_TO_RECEIVE',
  'WRONGFUL_REJECTION',
  'FORCE_MAJEURE',
  'MUTUAL_EXIT',
  'ACTIVATION_FAILURE',
] as const;
export type BreachReasonCode = (typeof BREACH_REASON_CODES)[number];

/** The contract's `remedyLeg.remedyType` enum. */
export const REMEDY_TYPES = [
  'NONE',
  'PAYMENT_SETTLEMENT',
  'PAYMENT_REFUND',
  'DEPOSIT_FORFEITURE',
  'CONTRACTUAL_PENALTY',
  'DAMAGES_COMPENSATION',
] as const;
export type RemedyType = (typeof REMEDY_TYPES)[number];

/** The contract's `remedyLeg.fundType` enum. */
export const FUND_TYPES = [
  'BUYER_DEPOSIT',
  'SELLER_DEPOSIT',
  'MILESTONE_PAYMENT',
] as const;
export type FundType = (typeof FUND_TYPES)[number];

export interface RemedyLeg {
  remedyLegId: string;
  remedyType: RemedyType;
  fundType: FundType;
  role: PartyRole;
  /** Non-negative VND with two decimals, as the contract's `money`. */
  amount: string;
}

/** `eventId` is the envelope's: the dedup key's event (design §5). */
export interface ContractTerminatedRequestedCommand {
  eventId: string;
  contractId: string;
  recipients: NotificationRecipient[];
  terminationType: TerminationType;
  requestedBy: TerminationRequester;
  finalBreachingRole: PartyRole | null;
  breachReasonCode: BreachReasonCode | null;
  remedyDecisionId: string | null;
  breachCaseId: string | null;
  affectedMilestoneIds: string[];
  supersededByContractId: string | null;
  replacesContractId: string | null;
  remedyLegs: RemedyLeg[];
}
