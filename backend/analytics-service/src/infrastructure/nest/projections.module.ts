import { Module } from '@nestjs/common';
import type { MysqlConnectionLike } from '@agricontract/eventing';
import type {
  ContractDimensionRepository,
  ContractOutcomeFactRepository,
  MilestoneFactRepository,
} from '../../application/port';
import {
  MysqlContractDimensionRepository,
  MysqlContractOutcomeFactRepository,
  MysqlMilestoneFactRepository,
} from '../persistence/mysqlProjections';
import { buildProjections } from '../persistence/projections';
import { CONTRACT_DIMENSION, CONTRACT_OUTCOME_FACTS, MILESTONE_FACTS, PROJECTIONS } from './tokens';

/**
 * Registers the projections as Nest providers: the repositories first, then the handler list built from
 * them (#133 dimension and milestone facts, #148 contract outcome facts).
 */
@Module({
  providers: [
    { provide: CONTRACT_DIMENSION, useFactory: () => new MysqlContractDimensionRepository() },
    { provide: MILESTONE_FACTS, useFactory: () => new MysqlMilestoneFactRepository() },
    { provide: CONTRACT_OUTCOME_FACTS, useFactory: () => new MysqlContractOutcomeFactRepository() },
    {
      provide: PROJECTIONS,
      useFactory: (
        contracts: ContractDimensionRepository<MysqlConnectionLike>,
        facts: MilestoneFactRepository<MysqlConnectionLike>,
        outcomes: ContractOutcomeFactRepository<MysqlConnectionLike>,
      ) => buildProjections(contracts, facts, outcomes),
      inject: [CONTRACT_DIMENSION, MILESTONE_FACTS, CONTRACT_OUTCOME_FACTS],
    },
  ],
  exports: [PROJECTIONS],
})
export class ProjectionsModule {}
