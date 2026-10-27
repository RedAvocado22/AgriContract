/** Analytics read-model rows (star schema, design §2.1 / §2.2). Plain data, no framework types. */

/** dim_contract: the local copy of what analytics needs to know about a contract. */
export interface ContractDimension {
  contractId: string;
  commodity: string;
  buyerId: string;
  sellerId: string;
  totalAmount: string;
  signedAt: Date;
}

export type MilestoneFactStatus = 'SETTLED' | 'CANCELLED_WITH_PENALTY';

/** fact_milestone_performance: one row per finished milestone. */
export interface MilestoneFact {
  factId: string;
  contractId: string;
  milestoneId: string;
  status: MilestoneFactStatus;
  lockedAmount: string;
  actualAmount: string;
  /** locked - actual */
  deltaShortfallAmount: string;
  hasForceMajeure: boolean;
  resolvedAt: Date;
}

/** contract.terminated terminationType (enum-ownership: termination taxonomy owned by contract-service). */
export type TerminationType =
  | 'WITHDRAW_OFFER'
  | 'MUTUAL_TERMINATION'
  | 'MUTUAL_REPLACEMENT'
  | 'TERMINATION_FOR_BREACH'
  | 'TERMINATION_FOR_FORCE_MAJEURE'
  | 'ACTIVATION_FAILURE';

export type PartyRole = 'BUYER' | 'SELLER';
/** Who started the termination. Never a statement about fault (milestone-escrow §6.0). */
export type RequestedBy = PartyRole | 'SYSTEM';

/**
 * fact_contract_termination (design §2.2): one row per terminated contract. Who asked to terminate
 * (requestedBy) and who was finally found in breach (finalBreachingRole) are separate columns, so a
 * default rate can never be computed from the party that pressed the button.
 */
export interface ContractTerminationFact {
  factId: string;
  contractId: string;
  terminationType: TerminationType;
  requestedBy: RequestedBy;
  finalBreachingRole: PartyRole | null;
  breachReasonCode: string | null;
  remedyDecisionId: string | null;
  terminatedAt: Date;
}

/** fact_contract_settlement (design §2.2): one row per contract whose milestones are all settled. */
export interface ContractSettlementFact {
  factId: string;
  contractId: string;
  settledAt: Date;
}

/**
 * Bucket of a termination in the monthly report (design §2.2 agg_monthly_commodity_stats, §3.4):
 *  - SELLER_BREACH / BUYER_BREACH: TERMINATION_FOR_BREACH, by finalBreachingRole (the only default buckets);
 *  - MUTUAL_EXIT: MUTUAL_TERMINATION / MUTUAL_REPLACEMENT, never a default;
 *  - NO_FAULT_EXIT: TERMINATION_FOR_FORCE_MAJEURE / ACTIVATION_FAILURE;
 *  - PRE_SIGN_WITHDRAWAL: WITHDRAW_OFFER, the contract was never signed, so it is outside every rate;
 *  - UNATTRIBUTED_BREACH: TERMINATION_FOR_BREACH without a final role. The contract says this does not
 *    happen (a case resolved with a null role does not terminate, matrix 26h); if it does, nobody is
 *    counted in breach instead of guessing one.
 */
export type TerminationOutcome =
  | 'SELLER_BREACH'
  | 'BUYER_BREACH'
  | 'MUTUAL_EXIT'
  | 'NO_FAULT_EXIT'
  | 'PRE_SIGN_WITHDRAWAL'
  | 'UNATTRIBUTED_BREACH';

/**
 * The one rule for counting defaults (matrix 26g). It reads terminationType and finalBreachingRole only:
 * requestedBy is never an input, and a role on a mutual / no-fault termination is ignored.
 */
export function terminationOutcome(fact: Pick<ContractTerminationFact, 'terminationType' | 'finalBreachingRole'>): TerminationOutcome {
  switch (fact.terminationType) {
    case 'TERMINATION_FOR_BREACH':
      if (fact.finalBreachingRole === 'SELLER') return 'SELLER_BREACH';
      if (fact.finalBreachingRole === 'BUYER') return 'BUYER_BREACH';
      return 'UNATTRIBUTED_BREACH';
    case 'MUTUAL_TERMINATION':
    case 'MUTUAL_REPLACEMENT':
      return 'MUTUAL_EXIT';
    case 'TERMINATION_FOR_FORCE_MAJEURE':
    case 'ACTIVATION_FAILURE':
      return 'NO_FAULT_EXIT';
    case 'WITHDRAW_OFFER':
      return 'PRE_SIGN_WITHDRAWAL';
  }
}

/** True only for a termination that counts in a breach (default) rate. */
export function countsAsDefault(outcome: TerminationOutcome): boolean {
  return outcome === 'SELLER_BREACH' || outcome === 'BUYER_BREACH';
}
