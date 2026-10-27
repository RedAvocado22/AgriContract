import { isDuplicateKey, type MysqlConnectionLike } from '@agricontract/eventing';
import type {
  ContractDimensionRepository,
  ContractOutcomeFactRepository,
  MilestoneFactRepository,
} from '../../application/port';
import type {
  ContractDimension,
  ContractSettlementFact,
  ContractTerminationFact,
  MilestoneFact,
} from '../../domain/projections';
import { toMysqlDateTime } from './ingestedEventStore';

/** dim_contract. */
export class MysqlContractDimensionRepository implements ContractDimensionRepository<MysqlConnectionLike> {
  async upsert(tx: MysqlConnectionLike, c: ContractDimension): Promise<void> {
    await tx.query(
      `INSERT INTO dim_contract (contract_id, commodity, buyer_id, seller_id, total_amount, signed_at)
       VALUES (?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE commodity = VALUES(commodity), buyer_id = VALUES(buyer_id),
         seller_id = VALUES(seller_id), total_amount = VALUES(total_amount), signed_at = VALUES(signed_at)`,
      [c.contractId, c.commodity, c.buyerId, c.sellerId, c.totalAmount, toMysqlDateTime(c.signedAt)],
    );
  }
}

/** fact_milestone_performance and pending_force_majeure_flag. */
export class MysqlMilestoneFactRepository implements MilestoneFactRepository<MysqlConnectionLike> {
  /**
   * Row lock on milestone_projection_lock (created on first use, kept after commit). The second
   * transaction for the same milestone waits here until the first commits; the reads below are
   * locking reads (FOR UPDATE) so they see what that first transaction committed, not an older
   * REPEATABLE READ snapshot.
   */
  async lockMilestone(tx: MysqlConnectionLike, milestoneId: string): Promise<void> {
    await tx.query(
      `INSERT INTO milestone_projection_lock (milestone_id) VALUES (?)
       ON DUPLICATE KEY UPDATE milestone_id = milestone_id`,
      [milestoneId],
    );
  }

  async insertIfAbsent(tx: MysqlConnectionLike, f: MilestoneFact): Promise<boolean> {
    try {
      await tx.query(
        `INSERT INTO fact_milestone_performance
           (fact_id, contract_id, milestone_id, status, locked_amount, actual_amount,
            delta_shortfall_amount, has_force_majeure, resolved_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          f.factId,
          f.contractId,
          f.milestoneId,
          f.status,
          f.lockedAmount,
          f.actualAmount,
          f.deltaShortfallAmount,
          f.hasForceMajeure,
          toMysqlDateTime(f.resolvedAt),
        ],
      );
      return true;
    } catch (err) {
      // A failed statement does not abort the surrounding transaction in MySQL.
      if (isDuplicateKey(err)) return false;
      throw err;
    }
  }

  async markForceMajeure(tx: MysqlConnectionLike, milestoneId: string): Promise<boolean> {
    await tx.query('UPDATE fact_milestone_performance SET has_force_majeure = TRUE WHERE milestone_id = ?', [milestoneId]);
    // "rows affected" is 0 for a row that is already TRUE on some drivers, so ask whether the row exists.
    const [rows] = await tx.query('SELECT 1 AS found FROM fact_milestone_performance WHERE milestone_id = ? FOR UPDATE', [milestoneId]);
    return (rows as unknown[]).length > 0;
  }

  async addPendingForceMajeure(tx: MysqlConnectionLike, milestoneId: string, flaggedAt: Date): Promise<void> {
    await tx.query(
      `INSERT INTO pending_force_majeure_flag (milestone_id, flagged_at) VALUES (?, ?)
       ON DUPLICATE KEY UPDATE flagged_at = flagged_at`,
      [milestoneId, toMysqlDateTime(flaggedAt)],
    );
  }

  async hasPendingForceMajeure(tx: MysqlConnectionLike, milestoneId: string): Promise<boolean> {
    const [rows] = await tx.query('SELECT 1 AS found FROM pending_force_majeure_flag WHERE milestone_id = ? FOR UPDATE', [milestoneId]);
    return (rows as unknown[]).length > 0;
  }

  async deletePendingForceMajeure(tx: MysqlConnectionLike, milestoneId: string): Promise<void> {
    await tx.query('DELETE FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId]);
  }
}

/** fact_contract_termination and fact_contract_settlement. */
export class MysqlContractOutcomeFactRepository implements ContractOutcomeFactRepository<MysqlConnectionLike> {
  async insertTerminationIfAbsent(tx: MysqlConnectionLike, f: ContractTerminationFact): Promise<boolean> {
    return insertIgnoringDuplicate(
      tx,
      `INSERT INTO fact_contract_termination
         (fact_id, contract_id, termination_type, requested_by, final_breaching_role, breach_reason_code,
          remedy_decision_id, terminated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        f.factId,
        f.contractId,
        f.terminationType,
        f.requestedBy,
        f.finalBreachingRole,
        f.breachReasonCode,
        f.remedyDecisionId,
        toMysqlDateTime(f.terminatedAt),
      ],
    );
  }

  async insertSettlementIfAbsent(tx: MysqlConnectionLike, f: ContractSettlementFact): Promise<boolean> {
    return insertIgnoringDuplicate(
      tx,
      'INSERT INTO fact_contract_settlement (fact_id, contract_id, settled_at) VALUES (?, ?, ?)',
      [f.factId, f.contractId, toMysqlDateTime(f.settledAt)],
    );
  }
}

/** Runs an INSERT; a duplicate key (same fact_id or same contract_id) means the fact exists and returns false. */
async function insertIgnoringDuplicate(tx: MysqlConnectionLike, sql: string, values: unknown[]): Promise<boolean> {
  try {
    await tx.query(sql, values);
    return true;
  } catch (err) {
    // A failed statement does not abort the surrounding transaction in MySQL.
    if (isDuplicateKey(err)) return false;
    throw err;
  }
}
