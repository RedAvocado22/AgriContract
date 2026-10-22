import { subtractMoney } from '../../domain/money';
import type { MilestoneFact } from '../../domain/projections';
import type { IngestEventCommand, MilestoneFactRepository, ProjectionHandler } from '../port';
import { stringField } from './payloadFields';

const ZERO = '0.00';

/**
 * milestone.settled and milestone.cancelled_with_penalty -> one row in fact_milestone_performance
 * (design §3.2). Before inserting it looks in pending_force_majeure_flag (a force-majeure approval always
 * arrives before the milestone ends) and merges the flag into the new row, then removes the staging row.
 * The fact_id is the event id and a milestone has at most one fact, so a replay of the same milestone
 * outcome cannot count its money twice.
 */
export class RecordMilestoneOutcome<Tx> implements ProjectionHandler<Tx> {
  private constructor(
    readonly eventType: 'milestone.settled' | 'milestone.cancelled_with_penalty',
    private readonly facts: MilestoneFactRepository<Tx>,
    private readonly build: (command: IngestEventCommand) => Pick<MilestoneFact, 'status' | 'lockedAmount' | 'actualAmount'>,
  ) {}

  static settled<Tx>(facts: MilestoneFactRepository<Tx>): RecordMilestoneOutcome<Tx> {
    return new RecordMilestoneOutcome('milestone.settled', facts, (c) => ({
      status: 'SETTLED',
      lockedAmount: stringField(c, 'lockedAmount'),
      actualAmount: stringField(c, 'actualAmount'),
    }));
  }

  /**
   * The event carries no lockedAmount (only the penalty), so the amounts are recorded as 0.00: the row
   * counts the cancellation without inventing money that the contract does not state.
   */
  static cancelledWithPenalty<Tx>(facts: MilestoneFactRepository<Tx>): RecordMilestoneOutcome<Tx> {
    return new RecordMilestoneOutcome('milestone.cancelled_with_penalty', facts, () => ({
      status: 'CANCELLED_WITH_PENALTY',
      lockedAmount: ZERO,
      actualAmount: ZERO,
    }));
  }

  async apply(command: IngestEventCommand, tx: Tx): Promise<void> {
    const milestoneId = stringField(command, 'milestoneId');
    const amounts = this.build(command);
    await this.facts.lockMilestone(tx, milestoneId);
    const hasForceMajeure = await this.facts.hasPendingForceMajeure(tx, milestoneId);
    const inserted = await this.facts.insertIfAbsent(tx, {
      factId: command.eventId,
      contractId: stringField(command, 'contractId'),
      milestoneId,
      ...amounts,
      deltaShortfallAmount: subtractMoney(amounts.lockedAmount, amounts.actualAmount),
      hasForceMajeure,
      resolvedAt: command.occurredAt,
    });
    if (inserted && hasForceMajeure) await this.facts.deletePendingForceMajeure(tx, milestoneId);
  }
}
