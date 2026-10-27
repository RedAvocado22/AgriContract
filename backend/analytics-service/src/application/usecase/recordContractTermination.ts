import type { PartyRole, RequestedBy, TerminationType } from '../../domain/projections';
import type { ContractOutcomeFactRepository, IngestEventCommand, ProjectionHandler } from '../port';
import { nullableStringField, stringField } from './payloadFields';

/**
 * contract.terminated -> one row in fact_contract_termination (design §3.3, matrix 26g).
 * terminationType, requestedBy and finalBreachingRole are copied into their own columns as the event states
 * them; requestedBy is never written into, or used to fill, the breaching-role column. Which terminations
 * count as a default is decided by terminationOutcome() in the domain.
 * The fact_id is the event id and a contract has at most one termination fact, so a redelivery (same event id,
 * stopped by the dedupe log) or a replay under a new event id is never counted twice.
 */
export class RecordContractTermination<Tx> implements ProjectionHandler<Tx> {
  readonly eventType = 'contract.terminated';

  constructor(private readonly facts: ContractOutcomeFactRepository<Tx>) {}

  async apply(command: IngestEventCommand, tx: Tx): Promise<void> {
    await this.facts.insertTerminationIfAbsent(tx, {
      factId: command.eventId,
      contractId: stringField(command, 'contractId'),
      // The enums were checked against the frozen contract at the messaging boundary.
      terminationType: stringField(command, 'terminationType') as TerminationType,
      requestedBy: stringField(command, 'requestedBy') as RequestedBy,
      finalBreachingRole: nullableStringField(command, 'finalBreachingRole') as PartyRole | null,
      breachReasonCode: nullableStringField(command, 'breachReasonCode'),
      remedyDecisionId: nullableStringField(command, 'remedyDecisionId'),
      // The payload has no termination time; the event time is when the contract became terminal.
      terminatedAt: command.occurredAt,
    });
  }
}
