/**
 * Issue #133: contract.signed -> dim_contract, milestone.settled / milestone.cancelled_with_penalty ->
 * fact_milestone_performance, milestone.force_majeure_resolved -> has_force_majeure. Runs through the same
 * production wiring (startAnalytics) as main.ts; only the broker and the database are in-memory fakes.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { FlagForceMajeure } from '../src/application/usecase/flagForceMajeure';
import { RecordMilestoneOutcome } from '../src/application/usecase/recordMilestoneOutcome';
import { RecordSignedContract } from '../src/application/usecase/recordSignedContract';
import { subtractMoney } from '../src/domain/money';
import { startAnalytics, type AnalyticsRuntime } from '../src/infrastructure/runtime';
import { CONTRACTS_EXCHANGE, deadLetterQueueName, queueName } from '../src/infrastructure/messaging/topology';
import {
  FakeBroker,
  InMemoryContractDimension,
  InMemoryDb,
  InMemoryIngestedEventStore,
  InMemoryMilestoneFacts,
  event,
  publishEvent,
  recordingLogger,
  type FakeTx,
} from './fakes';
import { deepEqual } from './deepEqual';

/** The only element of a list; fails the test otherwise. */
function one<T>(list: T[]): T {
  assert.equal(list.length, 1);
  return list[0]!;
}

describe('money arithmetic', () => {
  it('subtracts exactly, including negative and sub-unit amounts', () => {
    assert.equal(subtractMoney('60000000.00', '59500000.00'), '500000.00');
    assert.equal(subtractMoney('0.10', '0.30'), '-0.20');
    assert.equal(subtractMoney('0.00', '0.00'), '0.00');
    assert.equal(subtractMoney('100.00', '0.01'), '99.99');
    assert.equal(subtractMoney('9007199254740993.00', '1.00'), '9007199254740992.00');
  });
  it('rejects anything that is not a two-decimal amount', () => {
    assert.throws(() => subtractMoney('1', '0.00'));
    assert.throws(() => subtractMoney('1.5', '0.00'));
  });
});

describe('projections through the production wiring', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;
  const logger = recordingLogger();
  const runtimes: AnalyticsRuntime[] = [];

  beforeEach(() => {
    broker = new FakeBroker(20);
    db = new InMemoryDb();
    for (const r of runtimes.splice(0)) r.stopMonitor();
  });

  async function boot(): Promise<AnalyticsRuntime> {
    const contracts = new InMemoryContractDimension();
    const facts = new InMemoryMilestoneFacts();
    const runtime = await startAnalytics<FakeTx>({
      consumeChannel: broker,
      monitorChannel: broker,
      transactions: db,
      processedEvents: db,
      store: new InMemoryIngestedEventStore(),
      projections: [
        new RecordSignedContract(contracts),
        RecordMilestoneOutcome.settled(facts),
        RecordMilestoneOutcome.cancelledWithPenalty(facts),
        new FlagForceMajeure(facts),
      ],
      logger,
      dlqMonitorIntervalMs: 60_000,
      sleep: async () => {},
    });
    runtimes.push(runtime);
    return runtime;
  }
  const send = (e: ReturnType<typeof event>) => publishEvent(broker, CONTRACTS_EXCHANGE, e);

  it('contract.signed upserts dim_contract; a replay or a later copy does not duplicate the row', async () => {
    await boot();
    const contractId = randomUUID();
    const signed = event('contract.signed', contractId, { contractId, commodity: 'COFFEE', totalAmount: '120000000.00' });
    send(signed);
    send(signed); // at-least-once redelivery
    send(event('contract.signed', contractId, { contractId, commodity: 'COFFEE', totalAmount: '130000000.00' })); // new event id
    await broker.drain();
    assert.equal(db.committed.dimContract.length, 1);
    assert.equal(db.committed.dimContract[0]!.commodity, 'COFFEE');
    assert.equal(db.committed.dimContract[0]!.totalAmount, '130000000.00');
    assert.equal(db.committed.dimContract[0]!.signedAt.toISOString(), '2026-10-12T05:00:00.000Z');
  });

  it('milestone.settled writes one fact with delta_shortfall = locked - actual; duplicates are not counted twice', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    const settled = event('milestone.settled', contractId, { contractId, milestoneId, lockedAmount: '60000000.00', actualAmount: '59500000.00' });
    send(settled);
    send(settled); // same event id: dedupe log
    send(event('milestone.settled', contractId, { contractId, milestoneId, lockedAmount: '60000000.00', actualAmount: '59500000.00' })); // new id, same milestone
    await broker.drain();
    assert.equal(db.committed.milestoneFacts.length, 1);
    const fact = db.committed.milestoneFacts[0]!;
    assert.equal(fact.factId, settled.eventId);
    assert.equal(fact.status, 'SETTLED');
    assert.equal(fact.lockedAmount, '60000000.00');
    assert.equal(fact.actualAmount, '59500000.00');
    assert.equal(fact.deltaShortfallAmount, '500000.00');
    assert.equal(fact.hasForceMajeure, false);
    assert.equal(fact.resolvedAt.toISOString(), settled.occurredAt);
    assert.equal(db.committed.ingested.length, 2); // both distinct events are kept in the landing table
    assert.equal(broker.depth(deadLetterQueueName('milestone.settled')), 0);
  });

  it('milestone.cancelled_with_penalty writes a CANCELLED_WITH_PENALTY fact with actual 0.00', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    send(event('milestone.cancelled_with_penalty', contractId, { contractId, milestoneId }));
    await broker.drain();
    const fact = one(db.committed.milestoneFacts);
    assert.equal(fact.status, 'CANCELLED_WITH_PENALTY');
    assert.equal(fact.actualAmount, '0.00');
    assert.equal(fact.deltaShortfallAmount, fact.lockedAmount);
  });

  it('force majeure approved BEFORE the milestone ends is staged, merged into the new fact and the staging row removed', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    await broker.drain();
    assert.equal(db.committed.milestoneFacts.length, 0);
    deepEqual(db.committed.pendingForceMajeure.map((p) => p.milestoneId), [milestoneId]);

    send(event('milestone.settled', contractId, { contractId, milestoneId }));
    await broker.drain();
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, true);
    assert.equal(db.committed.pendingForceMajeure.length, 0);
  });

  it('force majeure also merges into a cancelled milestone, and a duplicate approval stages only one row', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' })); // new event id
    await broker.drain();
    assert.equal(db.committed.pendingForceMajeure.length, 1);
    send(event('milestone.cancelled_with_penalty', contractId, { contractId, milestoneId }));
    await broker.drain();
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, true);
    assert.equal(db.committed.pendingForceMajeure.length, 0);
  });

  it('force majeure arriving AFTER the fact exists updates the row and stages nothing', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    send(event('milestone.settled', contractId, { contractId, milestoneId }));
    await broker.drain();
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, false);
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'APPROVED' }));
    await broker.drain();
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, true);
    assert.equal(db.committed.pendingForceMajeure.length, 0);
  });

  it('a REJECTED force majeure changes nothing', async () => {
    await boot();
    const contractId = randomUUID();
    const milestoneId = randomUUID();
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId, decision: 'REJECTED' }));
    send(event('milestone.settled', contractId, { contractId, milestoneId }));
    await broker.drain();
    assert.equal(db.committed.pendingForceMajeure.length, 0);
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, false);
  });

  it('the flag belongs to its own milestone only', async () => {
    await boot();
    const contractId = randomUUID();
    const flagged = randomUUID();
    const other = randomUUID();
    send(event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId: flagged, decision: 'APPROVED' }));
    send(event('milestone.settled', contractId, { contractId, milestoneId: other }));
    await broker.drain();
    assert.equal(one(db.committed.milestoneFacts).hasForceMajeure, false);
    deepEqual(db.committed.pendingForceMajeure.map((p) => p.milestoneId), [flagged]);
  });

  it('restart catch-up: events published while analytics is stopped (producer keeps working) are all projected once', async () => {
    const first = await boot();
    await first.stopConsuming(); // analytics is down; contract-service keeps publishing and never blocks
    const contractId = randomUUID();
    const m1 = randomUUID();
    const m2 = randomUUID();
    const backlog = [
      event('contract.signed', contractId, { contractId }),
      event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId: m1, decision: 'APPROVED' }),
      event('milestone.settled', contractId, { contractId, milestoneId: m1 }),
      event('milestone.cancelled_with_penalty', contractId, { contractId, milestoneId: m2 }),
    ];
    for (const e of backlog) assert.equal(send(e), true);
    for (const e of backlog) send(e); // duplicates in the backlog
    assert.equal(db.committed.milestoneFacts.length, 0);

    await boot(); // restart
    await broker.drain();

    assert.equal(db.committed.dimContract.length, 1);
    assert.equal(db.committed.milestoneFacts.length, 2);
    assert.equal(db.committed.milestoneFacts.find((f) => f.milestoneId === m1)!.hasForceMajeure, true);
    assert.equal(db.committed.milestoneFacts.find((f) => f.milestoneId === m2)!.hasForceMajeure, false);
    assert.equal(db.committed.pendingForceMajeure.length, 0);
    assert.equal(broker.unackedCount(), 0);
  });

  it('events for the same contract end up in the same state in any arrival order', async () => {
    const contractId = randomUUID();
    const m = randomUUID();
    const events = () => [
      event('contract.signed', contractId, { contractId }),
      event('milestone.force_majeure_resolved', contractId, { contractId, milestoneId: m, decision: 'APPROVED' }),
      event('milestone.settled', contractId, { contractId, milestoneId: m }),
    ];
    const outcomes: string[] = [];
    for (const order of [[0, 1, 2], [2, 1, 0], [1, 2, 0], [2, 0, 1]]) {
      db = new InMemoryDb();
      broker = new FakeBroker(20);
      await boot();
      const list = events();
      for (const i of order) send(list[i]!);
      await broker.drain();
      const f = one(db.committed.milestoneFacts);
      outcomes.push(`${db.committed.dimContract.length}|${f.hasForceMajeure}|${db.committed.pendingForceMajeure.length}`);
      for (const r of runtimes.splice(0)) r.stopMonitor();
    }
    deepEqual(new Set(outcomes), new Set(['1|true|0']));
  });

  it('a projection failure rolls everything back: no landing row, no fact, no dedupe row; the retry succeeds', async () => {
    let failures = 1;
    const facts = new InMemoryMilestoneFacts();
    const flaky = {
      ...facts,
      insertIfAbsent: async (tx: FakeTx, f: Parameters<InMemoryMilestoneFacts['insertIfAbsent']>[1]) => {
        await facts.insertIfAbsent(tx, f);
        if (failures-- > 0) throw new Error('deadlock');
        return true;
      },
      lockMilestone: facts.lockMilestone.bind(facts),
      markForceMajeure: facts.markForceMajeure.bind(facts),
      addPendingForceMajeure: facts.addPendingForceMajeure.bind(facts),
      hasPendingForceMajeure: facts.hasPendingForceMajeure.bind(facts),
      deletePendingForceMajeure: facts.deletePendingForceMajeure.bind(facts),
    };
    runtimes.push(
      await startAnalytics<FakeTx>({
        consumeChannel: broker,
        monitorChannel: broker,
        transactions: db,
        processedEvents: db,
        store: new InMemoryIngestedEventStore(),
        projections: [RecordMilestoneOutcome.settled(flaky)],
        logger,
        dlqMonitorIntervalMs: 60_000,
        sleep: async () => {},
      }),
    );
    const contractId = randomUUID();
    send(event('milestone.settled', contractId, { contractId }));
    await broker.drain();
    assert.equal(db.committed.milestoneFacts.length, 1); // attempt 1 rolled back, attempt 2 committed
    assert.equal(db.committed.ingested.length, 1);
    assert.equal(db.committed.processed.length, 1);
    assert.equal(broker.depth(deadLetterQueueName('milestone.settled')), 0);
  });

  it('a malformed event (missing amounts) goes to the DLQ and writes no fact', async () => {
    await boot();
    const bad = event('milestone.settled', randomUUID());
    delete (bad.payload as Record<string, unknown>)['actualAmount'];
    send(bad);
    await broker.drain();
    assert.equal(db.committed.milestoneFacts.length, 0);
    assert.equal(broker.depth(deadLetterQueueName('milestone.settled')), 1);
    assert.equal(broker.depth(queueName('milestone.settled')), 0);
  });
});
