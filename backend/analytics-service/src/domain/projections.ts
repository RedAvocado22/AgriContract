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
