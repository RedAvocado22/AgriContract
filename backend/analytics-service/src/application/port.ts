import type {
  ContractDimension,
  ContractSettlementFact,
  ContractTerminationFact,
  MilestoneFact,
} from '../domain/projections';

/**
 * What the ingest use case needs from the outside, and what it receives. The messaging adapter validates
 * the RabbitMQ message and maps it to an IngestEventCommand, so nothing in application/ sees a raw
 * broker payload (rules §3, §4B).
 */

/** A canonical event that already passed envelope and payload validation at the messaging boundary. */
export interface IngestEventCommand {
  eventId: string;
  eventType: string;
  eventVersion: number;
  producer: string;
  aggregateId: string;
  occurredAt: Date;
  correlationId: string | null;
  causationId: string | null;
  /** Validated payload; contact PII (recipient e-mail addresses) is removed before this point. */
  payload: Readonly<Record<string, unknown>>;
}

/**
 * Durable landing table for every ingested event (analytics_ingested_event). Written inside the same
 * DB transaction as the idempotency-log row, so an event is either fully ingested or not at all.
 */
export interface IngestedEventStore<Tx> {
  record(tx: Tx, command: IngestEventCommand): Promise<void>;
}

/**
 * Projection work for one event type (fact and dimension tables). Runs in the same transaction as the
 * ingest write. Registered by #133 and #148; event types without a handler are still ingested.
 */
export interface ProjectionHandler<Tx> {
  readonly eventType: string;
  apply(command: IngestEventCommand, tx: Tx): Promise<void>;
}

/** dim_contract (design §2.1). */
export interface ContractDimensionRepository<Tx> {
  /** Insert, or overwrite the row with the same contract_id. */
  upsert(tx: Tx, contract: ContractDimension): Promise<void>;
}

/** fact_milestone_performance and its pending_force_majeure_flag staging table (design §2.2, §2.4). */
export interface MilestoneFactRepository<Tx> {
  /**
   * Serialize every projection that touches this milestone until the transaction ends. Must be the first
   * call of a handler: a settlement and a force-majeure approval of the same milestone can be consumed in
   * parallel, and each one's decision depends on what the other has (or has not yet) committed.
   */
  lockMilestone(tx: Tx, milestoneId: string): Promise<void>;
  /** Insert the fact unless the milestone already has one. Returns true when a row was written. */
  insertIfAbsent(tx: Tx, fact: MilestoneFact): Promise<boolean>;
  /** Set has_force_majeure on the milestone's fact row. Returns false when there is no such row yet. */
  markForceMajeure(tx: Tx, milestoneId: string): Promise<boolean>;
  /** Remember a force-majeure approval for a milestone that has no fact row yet (idempotent). */
  addPendingForceMajeure(tx: Tx, milestoneId: string, flaggedAt: Date): Promise<void>;
  hasPendingForceMajeure(tx: Tx, milestoneId: string): Promise<boolean>;
  deletePendingForceMajeure(tx: Tx, milestoneId: string): Promise<void>;
}

/** fact_contract_termination and fact_contract_settlement (design §2.2, §3.3). */
export interface ContractOutcomeFactRepository<Tx> {
  /** Insert unless the contract already has a termination fact. Returns true when a row was written. */
  insertTerminationIfAbsent(tx: Tx, fact: ContractTerminationFact): Promise<boolean>;
  /** Insert unless the contract already has a settlement fact. Returns true when a row was written. */
  insertSettlementIfAbsent(tx: Tx, fact: ContractSettlementFact): Promise<boolean>;
}
