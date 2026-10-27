import type { MysqlConnectionLike } from '@agricontract/eventing';
import type {
  ContractDimensionRepository,
  ContractOutcomeFactRepository,
  MilestoneFactRepository,
  ProjectionHandler,
} from '../../application/port';
import { FlagForceMajeure } from '../../application/usecase/flagForceMajeure';
import { RecordContractSettlement } from '../../application/usecase/recordContractSettlement';
import { RecordContractTermination } from '../../application/usecase/recordContractTermination';
import { RecordMilestoneOutcome } from '../../application/usecase/recordMilestoneOutcome';
import { RecordSignedContract } from '../../application/usecase/recordSignedContract';

/**
 * Projection handlers registered at startup (they run in the same DB transaction as the ingest write):
 *  - #133: contract.signed (dim_contract), milestone.settled / milestone.cancelled_with_penalty
 *    (fact_milestone_performance) and milestone.force_majeure_resolved (has_force_majeure);
 *  - #148: contract.settled (fact_contract_settlement) and contract.terminated (fact_contract_termination).
 * Events without a handler are still consumed, validated, deduplicated and stored in analytics_ingested_event.
 * The Nest ProjectionsModule calls this with the repositories it provides.
 */
export function buildProjections(
  contracts: ContractDimensionRepository<MysqlConnectionLike>,
  milestoneFacts: MilestoneFactRepository<MysqlConnectionLike>,
  contractOutcomes: ContractOutcomeFactRepository<MysqlConnectionLike>,
): ProjectionHandler<MysqlConnectionLike>[] {
  return [
    new RecordSignedContract(contracts),
    RecordMilestoneOutcome.settled(milestoneFacts),
    RecordMilestoneOutcome.cancelledWithPenalty(milestoneFacts),
    new FlagForceMajeure(milestoneFacts),
    new RecordContractSettlement(contractOutcomes),
    new RecordContractTermination(contractOutcomes),
  ];
}
