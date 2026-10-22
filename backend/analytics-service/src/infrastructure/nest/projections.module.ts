import { Module } from '@nestjs/common';
import type { MysqlConnectionLike } from '@agricontract/eventing';
import type { ContractDimensionRepository, MilestoneFactRepository } from '../../application/port';
import { MysqlContractDimensionRepository, MysqlMilestoneFactRepository } from '../persistence/mysqlProjections';
import { buildProjections } from '../persistence/projections';
import { CONTRACT_DIMENSION, MILESTONE_FACTS, PROJECTIONS } from './tokens';

/**
 * Registers the projections as Nest providers: the repositories first, then the handler list built from
 * them. #148 adds its repositories and handlers here.
 */
@Module({
  providers: [
    { provide: CONTRACT_DIMENSION, useFactory: () => new MysqlContractDimensionRepository() },
    { provide: MILESTONE_FACTS, useFactory: () => new MysqlMilestoneFactRepository() },
    {
      provide: PROJECTIONS,
      useFactory: (
        contracts: ContractDimensionRepository<MysqlConnectionLike>,
        facts: MilestoneFactRepository<MysqlConnectionLike>,
      ) => buildProjections(contracts, facts),
      inject: [CONTRACT_DIMENSION, MILESTONE_FACTS],
    },
  ],
  exports: [PROJECTIONS],
})
export class ProjectionsModule {}
