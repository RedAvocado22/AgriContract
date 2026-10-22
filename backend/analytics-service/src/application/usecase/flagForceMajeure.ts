import type { IngestEventCommand, MilestoneFactRepository, ProjectionHandler } from '../port';
import { stringField } from './payloadFields';

/**
 * milestone.force_majeure_resolved -> has_force_majeure (design §2.4, §3.2). Only APPROVED decisions count.
 * Takes the per-milestone lock first so it cannot interleave with the settlement of the same milestone.
 * If the milestone's fact row exists it is updated; if not (the usual case) the flag waits in
 * pending_force_majeure_flag until the settled / cancelled event inserts the row.
 */
export class FlagForceMajeure<Tx> implements ProjectionHandler<Tx> {
  readonly eventType = 'milestone.force_majeure_resolved';

  constructor(private readonly facts: MilestoneFactRepository<Tx>) {}

  async apply(command: IngestEventCommand, tx: Tx): Promise<void> {
    if (stringField(command, 'decision') !== 'APPROVED') return;
    const milestoneId = stringField(command, 'milestoneId');
    await this.facts.lockMilestone(tx, milestoneId);
    if (await this.facts.markForceMajeure(tx, milestoneId)) return;
    await this.facts.addPendingForceMajeure(tx, milestoneId, command.occurredAt);
  }
}
