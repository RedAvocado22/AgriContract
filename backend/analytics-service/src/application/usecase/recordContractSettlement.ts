import type { ContractOutcomeFactRepository, IngestEventCommand, ProjectionHandler } from '../port';
import { stringField } from './payloadFields';

/**
 * contract.settled -> one row in fact_contract_settlement (design §3.3). This is the only source for
 * "contracts completed" at contract granularity (the breach-rate denominator); it is not derived from
 * fact_milestone_performance. A contract has at most one settlement fact, so replays are not counted twice.
 */
export class RecordContractSettlement<Tx> implements ProjectionHandler<Tx> {
  readonly eventType = 'contract.settled';

  constructor(private readonly facts: ContractOutcomeFactRepository<Tx>) {}

  async apply(command: IngestEventCommand, tx: Tx): Promise<void> {
    await this.facts.insertSettlementIfAbsent(tx, {
      factId: command.eventId,
      contractId: stringField(command, 'contractId'),
      settledAt: command.occurredAt,
    });
  }
}
