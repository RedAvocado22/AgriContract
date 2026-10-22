/**
 * The #133 projections against a real MySQL (the SQL itself: unique keys, upsert, UPDATE/SELECT, staging merge).
 * Runs only when ANALYTICS_TEST_MYSQL_URL is set, e.g. with the docker-compose MySQL:
 *   ANALYTICS_TEST_MYSQL_URL=mysql://root:root@localhost:3306/analytics_test npm test
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import mysql from 'mysql2/promise';
import { MysqlTransactionRunner, type MysqlConnectionLike } from '@agricontract/eventing';
import { MysqlContractDimensionRepository, MysqlMilestoneFactRepository } from '../src/infrastructure/persistence/mysqlProjections';
import { buildProjections } from '../src/infrastructure/persistence/projections';
import { event } from './fakes';
import { createTestDatabase, type TestDatabase } from './mysqlTestDb';

const URL = process.env.ANALYTICS_TEST_MYSQL_URL;
const PROJECTIONS = buildProjections(new MysqlContractDimensionRepository(), new MysqlMilestoneFactRepository());

(URL ? describe : describe.skip)('real MySQL projections (ANALYTICS_TEST_MYSQL_URL)', () => {
  let pool: mysql.Pool;
  let runner: MysqlTransactionRunner;
  let testDb: TestDatabase;

  beforeAll(async () => {
    testDb = await createTestDatabase(URL!); // private database, all migrations applied from empty
    pool = mysql.createPool({ uri: testDb.url, connectionLimit: 4, dateStrings: true, timezone: 'Z' });
    runner = new MysqlTransactionRunner(pool);
  });
  afterAll(async () => {
    await pool?.end();
    await testDb?.drop();
  });
  beforeEach(async () => {
    for (const t of ['dim_contract', 'fact_milestone_performance', 'pending_force_majeure_flag', 'milestone_projection_lock']) await pool.query(`DELETE FROM ${t}`);
  });

  const toCommand = (e: ReturnType<typeof event>) => ({
    eventId: e.eventId,
    eventType: e.eventType,
    eventVersion: e.eventVersion,
    producer: e.producer,
    aggregateId: e.aggregateId,
    occurredAt: new Date(e.occurredAt),
    correlationId: null,
    causationId: null,
    payload: e.payload as Record<string, unknown>,
  });
  /** Runs the event's projection(s) in its own transaction; `handlers` lets a test swap in a gated repository. */
  const applyWith = (handlers: typeof PROJECTIONS, e: ReturnType<typeof event>) =>
    runner.inTransaction(async (tx: MysqlConnectionLike) => {
      for (const p of handlers) if (p.eventType === e.eventType) await p.apply(toCommand(e), tx);
    });
  const apply = (e: ReturnType<typeof event>) => applyWith(PROJECTIONS, e);
  const rows = async (sql: string, values: unknown[] = []) => (await pool.query(sql, values))[0] as Array<Record<string, unknown>>;

  it('contract.signed upserts dim_contract', async () => {
    const contractId = randomUUID();
    await apply(event('contract.signed', contractId, { contractId, totalAmount: '120000000.00' }));
    await apply(event('contract.signed', contractId, { contractId, totalAmount: '130000000.00' }));
    const r = await rows('SELECT * FROM dim_contract WHERE contract_id = ?', [contractId]);
    assert.equal(r.length, 1);
    assert.equal(r[0]!['total_amount'], '130000000.00');
    assert.equal(r[0]!['commodity'], 'COFFEE');
  });

  it('settled: delta = locked - actual, a replayed milestone is not counted twice, staged force majeure is merged and removed', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    await apply(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    assert.equal((await rows('SELECT * FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId])).length, 1);
    const payload = { contractId, milestoneId, lockedAmount: '60000000.00', actualAmount: '59500000.00' };
    await apply(event('milestone.settled', contractId, payload));
    await apply(event('milestone.settled', contractId, payload)); // new event id, same milestone
    const facts = await rows('SELECT * FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId]);
    assert.equal(facts.length, 1);
    assert.equal(facts[0]!['delta_shortfall_amount'], '500000.00');
    assert.equal(facts[0]!['status'], 'SETTLED');
    assert.equal(Number(facts[0]!['has_force_majeure']), 1);
    assert.equal((await rows('SELECT * FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId])).length, 0);
  });

  it('force majeure after the fact exists updates the row (twice is fine) and stages nothing; cancelled facts use actual 0.00', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    await apply(event('milestone.cancelled_with_penalty', contractId, { contractId, milestoneId }));
    await apply(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    await apply(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    const facts = await rows('SELECT * FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId]);
    assert.equal(facts.length, 1);
    assert.equal(facts[0]!['status'], 'CANCELLED_WITH_PENALTY');
    assert.equal(facts[0]!['actual_amount'], '0.00');
    assert.equal(Number(facts[0]!['has_force_majeure']), 1);
    assert.equal((await rows('SELECT * FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId])).length, 0);
  });

  it('a failed projection rolls back its transaction', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    await assert.rejects(
      runner.inTransaction(async (tx) => {
        await PROJECTIONS.find((p) => p.eventType === 'milestone.settled')!.apply(
          {
            ...{ eventVersion: 1, producer: 'contract-service', aggregateId: contractId, correlationId: null, causationId: null },
            eventId: randomUUID(),
            eventType: 'milestone.settled',
            occurredAt: new Date(),
            payload: { contractId, milestoneId, lockedAmount: '1.00', actualAmount: '1.00' },
          },
          tx,
        );
        throw new Error('later step failed');
      }),
    );
    assert.equal((await rows('SELECT * FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId])).length, 0);
  });
  /** A promise that can be released from outside. */
  const gate = () => {
    let open!: () => void;
    const promise = new Promise<void>((r) => (open = r));
    return { promise, open };
  };
  const settle = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
  const handlersWith = (facts: MysqlMilestoneFactRepository) => buildProjections(new MysqlContractDimensionRepository(), facts);

  it('overlapping transactions: settlement has read "no flag yet" while the approval arrives -> the flag is not lost', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    const reached = gate();
    const release = gate();
    class PausedAfterPendingRead extends MysqlMilestoneFactRepository {
      override async hasPendingForceMajeure(tx: MysqlConnectionLike, id: string): Promise<boolean> {
        const found = await super.hasPendingForceMajeure(tx, id);
        reached.open();
        await release.promise; // the settlement is now between "no flag" and its INSERT
        return found;
      }
    }
    const settled = applyWith(
      handlersWith(new PausedAfterPendingRead()),
      event('milestone.settled', contractId, { contractId, milestoneId, lockedAmount: '10.00', actualAmount: '10.00' }),
    );
    await reached.promise;
    let approvalDone = false;
    const approval = apply(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' })).then(() => {
      approvalDone = true;
    });
    await settle(400);
    assert.equal(approvalDone, false, 'the approval must wait for the settlement of the same milestone');
    release.open();
    await Promise.all([settled, approval]);
    const facts = await rows('SELECT * FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId]);
    assert.equal(facts.length, 1);
    assert.equal(Number(facts[0]!['has_force_majeure']), 1);
    assert.equal((await rows('SELECT * FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId])).length, 0);
  });

  it('overlapping transactions: approval found no fact row yet while the settlement arrives -> the flag is merged', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    const reached = gate();
    const release = gate();
    class PausedAfterMissingRow extends MysqlMilestoneFactRepository {
      override async markForceMajeure(tx: MysqlConnectionLike, id: string): Promise<boolean> {
        const found = await super.markForceMajeure(tx, id);
        reached.open();
        await release.promise; // the approval is now between "no fact row" and staging the flag
        return found;
      }
    }
    const approval = applyWith(
      handlersWith(new PausedAfterMissingRow()),
      event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }),
    );
    await reached.promise;
    let settlementDone = false;
    const settlement = apply(
      event('milestone.settled', contractId, { contractId, milestoneId, lockedAmount: '10.00', actualAmount: '9.00' }),
    ).then(() => {
      settlementDone = true;
    });
    await settle(400);
    assert.equal(settlementDone, false, 'the settlement must wait for the approval of the same milestone');
    release.open();
    await Promise.all([approval, settlement]);
    const facts = await rows('SELECT * FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId]);
    assert.equal(facts.length, 1);
    assert.equal(Number(facts[0]!['has_force_majeure']), 1);
    assert.equal((await rows('SELECT * FROM pending_force_majeure_flag WHERE milestone_id = ?', [milestoneId])).length, 0);
  });

  it('money columns hold 18 digits: a 16-digit amount round-trips exactly', async () => {
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    await apply(event('contract.signed', contractId, { contractId, totalAmount: '9999999999999999.99' }));
    await apply(event('milestone.settled', contractId, { contractId, milestoneId, lockedAmount: '9999999999999999.99', actualAmount: '1.01' }));
    assert.equal((await rows('SELECT total_amount FROM dim_contract WHERE contract_id = ?', [contractId]))[0]!['total_amount'], '9999999999999999.99');
    assert.equal((await rows('SELECT delta_shortfall_amount FROM fact_milestone_performance WHERE milestone_id = ?', [milestoneId]))[0]!['delta_shortfall_amount'], '9999999999999998.98');
  });
});
