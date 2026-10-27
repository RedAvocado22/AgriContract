/**
 * Issue #148 (matrix 26g): contract.terminated -> fact_contract_termination, contract.settled ->
 * fact_contract_settlement. Runs through the same production wiring (startAnalytics) as main.ts; only the
 * broker and the database are in-memory fakes.
 *
 * Acceptance criteria:
 *  - requestedBy and finalBreachingRole are separate;
 *  - mutual outcomes do not count as default;
 *  - replay is ignored.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { RecordContractSettlement } from '../src/application/usecase/recordContractSettlement';
import { RecordContractTermination } from '../src/application/usecase/recordContractTermination';
import {
  countsAsDefault,
  terminationOutcome,
  type ContractTerminationFact,
  type PartyRole,
  type TerminationOutcome,
  type TerminationType,
} from '../src/domain/projections';
import { startAnalytics, type AnalyticsRuntime } from '../src/infrastructure/runtime';
import { CONTRACTS_EXCHANGE, deadLetterQueueName } from '../src/infrastructure/messaging/topology';
import {
  FakeBroker,
  InMemoryContractOutcomeFacts,
  InMemoryDb,
  InMemoryIngestedEventStore,
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

/** contract.terminated payload for a branch; attribution is null unless given (x-nullability). */
function terminated(
  contractId: string,
  terminationType: TerminationType,
  requestedBy: 'BUYER' | 'SELLER' | 'SYSTEM',
  finalBreachingRole: PartyRole | null = null,
  breachReasonCode: string | null = null,
) {
  const breach = terminationType === 'TERMINATION_FOR_BREACH';
  const replacement = terminationType === 'MUTUAL_REPLACEMENT';
  return event('contract.terminated', contractId, {
    contractId,
    terminationType,
    requestedBy,
    finalBreachingRole,
    breachReasonCode,
    remedyDecisionId: breach ? randomUUID() : null,
    breachCaseId: breach ? randomUUID() : null,
    supersededByContractId: replacement ? randomUUID() : null,
    replacesContractId: null,
  });
}

describe('terminationOutcome (the default-rate rule)', () => {
  const fact = (terminationType: TerminationType, finalBreachingRole: PartyRole | null) => ({ terminationType, finalBreachingRole });

  it('only TERMINATION_FOR_BREACH with a final role is a default, attributed to that role', () => {
    assert.equal(terminationOutcome(fact('TERMINATION_FOR_BREACH', 'SELLER')), 'SELLER_BREACH');
    assert.equal(terminationOutcome(fact('TERMINATION_FOR_BREACH', 'BUYER')), 'BUYER_BREACH');
    assert.equal(countsAsDefault('SELLER_BREACH'), true);
    assert.equal(countsAsDefault('BUYER_BREACH'), true);
  });

  it('mutual outcomes are never a default, even if a role is (wrongly) present', () => {
    for (const type of ['MUTUAL_TERMINATION', 'MUTUAL_REPLACEMENT'] as const) {
      for (const role of [null, 'BUYER', 'SELLER'] as const) {
        assert.equal(terminationOutcome(fact(type, role)), 'MUTUAL_EXIT');
      }
    }
    assert.equal(countsAsDefault('MUTUAL_EXIT'), false);
  });

  it('force majeure and activation failure are no-fault; a pre-sign withdrawal is outside every rate', () => {
    assert.equal(terminationOutcome(fact('TERMINATION_FOR_FORCE_MAJEURE', null)), 'NO_FAULT_EXIT');
    assert.equal(terminationOutcome(fact('ACTIVATION_FAILURE', null)), 'NO_FAULT_EXIT');
    assert.equal(terminationOutcome(fact('WITHDRAW_OFFER', null)), 'PRE_SIGN_WITHDRAWAL');
    const notDefaults: TerminationOutcome[] = ['NO_FAULT_EXIT', 'PRE_SIGN_WITHDRAWAL', 'UNATTRIBUTED_BREACH'];
    for (const o of notDefaults) assert.equal(countsAsDefault(o), false);
  });

  it('a breach termination without a final role blames nobody', () => {
    assert.equal(terminationOutcome(fact('TERMINATION_FOR_BREACH', null)), 'UNATTRIBUTED_BREACH');
  });

  it('does not read requestedBy at all', () => {
    const base: ContractTerminationFact = {
      factId: randomUUID(),
      contractId: randomUUID(),
      terminationType: 'TERMINATION_FOR_BREACH',
      requestedBy: 'SELLER',
      finalBreachingRole: 'BUYER',
      breachReasonCode: 'FUNDING_FAILURE',
      remedyDecisionId: randomUUID(),
      terminatedAt: new Date(),
    };
    for (const requestedBy of ['BUYER', 'SELLER', 'SYSTEM'] as const) {
      const fact: ContractTerminationFact = { ...base, requestedBy };
      assert.equal(terminationOutcome(fact), 'BUYER_BREACH');
    }
  });
});

describe('contract outcome facts through the production wiring', () => {
  let broker: FakeBroker;
  let db: InMemoryDb;
  const logger = recordingLogger();
  const runtimes: AnalyticsRuntime[] = [];

  beforeEach(() => {
    broker = new FakeBroker(20);
    db = new InMemoryDb();
    for (const r of runtimes.splice(0)) r.stopMonitor();
  });
  afterAll(() => {
    for (const r of runtimes.splice(0)) r.stopMonitor();
  });

  async function boot(): Promise<AnalyticsRuntime> {
    const outcomes = new InMemoryContractOutcomeFacts();
    const runtime = await startAnalytics<FakeTx>({
      consumeChannel: broker,
      monitorChannel: broker,
      transactions: db,
      processedEvents: db,
      store: new InMemoryIngestedEventStore(),
      projections: [new RecordContractSettlement(outcomes), new RecordContractTermination(outcomes)],
      logger,
      dlqMonitorIntervalMs: 60_000,
      sleep: async () => {},
    });
    runtimes.push(runtime);
    return runtime;
  }
  const send = (e: ReturnType<typeof event>) => publishEvent(broker, CONTRACTS_EXCHANGE, e);

  it('requestedBy and finalBreachingRole are separate: seller requests termination, buyer is found in breach (matrix 11h)', async () => {
    await boot();
    const contractId = randomUUID();
    const e = terminated(contractId, 'TERMINATION_FOR_BREACH', 'SELLER', 'BUYER', 'FUNDING_FAILURE');
    send(e);
    await broker.drain();
    const fact = one(db.committed.contractTerminations);
    assert.equal(fact.factId, e.eventId);
    assert.equal(fact.contractId, contractId);
    assert.equal(fact.terminationType, 'TERMINATION_FOR_BREACH');
    assert.equal(fact.requestedBy, 'SELLER');
    assert.equal(fact.finalBreachingRole, 'BUYER');
    assert.equal(fact.breachReasonCode, 'FUNDING_FAILURE');
    assert.equal(fact.remedyDecisionId, e.payload['remedyDecisionId']);
    assert.equal(fact.terminatedAt.toISOString(), e.occurredAt);
    assert.equal(terminationOutcome(fact), 'BUYER_BREACH');
  });

  it('the mirror case: buyer requests, seller is found in breach', async () => {
    await boot();
    const contractId = randomUUID();
    send(terminated(contractId, 'TERMINATION_FOR_BREACH', 'BUYER', 'SELLER', 'NON_DELIVERY'));
    await broker.drain();
    const fact = one(db.committed.contractTerminations);
    assert.equal(fact.requestedBy, 'BUYER');
    assert.equal(fact.finalBreachingRole, 'SELLER');
    assert.equal(terminationOutcome(fact), 'SELLER_BREACH');
  });

  it('every termination branch is stored; only the two attributed breaches count as default (matrix 26g)', async () => {
    await boot();
    const branches: Array<[ReturnType<typeof event>, TerminationOutcome]> = [
      [terminated(randomUUID(), 'TERMINATION_FOR_BREACH', 'BUYER', 'SELLER', 'NON_DELIVERY'), 'SELLER_BREACH'],
      [terminated(randomUUID(), 'TERMINATION_FOR_BREACH', 'SELLER', 'BUYER', 'FUNDING_FAILURE'), 'BUYER_BREACH'],
      [terminated(randomUUID(), 'MUTUAL_TERMINATION', 'BUYER'), 'MUTUAL_EXIT'],
      [terminated(randomUUID(), 'MUTUAL_REPLACEMENT', 'SELLER'), 'MUTUAL_EXIT'],
      [terminated(randomUUID(), 'TERMINATION_FOR_FORCE_MAJEURE', 'SELLER'), 'NO_FAULT_EXIT'],
      [terminated(randomUUID(), 'ACTIVATION_FAILURE', 'SYSTEM'), 'NO_FAULT_EXIT'],
      [terminated(randomUUID(), 'WITHDRAW_OFFER', 'BUYER'), 'PRE_SIGN_WITHDRAWAL'],
    ];
    for (const [e] of branches) send(e);
    await broker.drain();

    assert.equal(db.committed.contractTerminations.length, branches.length);
    for (const [e, expected] of branches) {
      const fact = db.committed.contractTerminations.find((f) => f.factId === e.eventId)!;
      assert.equal(terminationOutcome(fact), expected, `${e.payload['terminationType']}`);
    }
    const defaults = db.committed.contractTerminations.filter((f) => countsAsDefault(terminationOutcome(f)));
    deepEqual(defaults.map((f) => f.finalBreachingRole).sort(), ['BUYER', 'SELLER']);
  });

  it('a mutual exit keeps who requested it but has no breaching role and does not count as default', async () => {
    await boot();
    const contractId = randomUUID();
    send(terminated(contractId, 'MUTUAL_TERMINATION', 'SELLER'));
    await broker.drain();
    const fact = one(db.committed.contractTerminations);
    assert.equal(fact.requestedBy, 'SELLER');
    assert.equal(fact.finalBreachingRole, null);
    assert.equal(fact.breachReasonCode, null);
    assert.equal(fact.remedyDecisionId, null);
    assert.equal(countsAsDefault(terminationOutcome(fact)), false);
  });

  it('replay is ignored: a redelivery (same event id) and a replay under a new event id write one termination fact', async () => {
    await boot();
    const contractId = randomUUID();
    const first = terminated(contractId, 'TERMINATION_FOR_BREACH', 'BUYER', 'SELLER', 'NON_DELIVERY');
    send(first);
    send(first); // at-least-once redelivery: stopped by the dedupe log
    send(terminated(contractId, 'TERMINATION_FOR_BREACH', 'BUYER', 'SELLER', 'NON_DELIVERY')); // new id, same contract
    await broker.drain();
    const fact = one(db.committed.contractTerminations);
    assert.equal(fact.factId, first.eventId);
    assert.equal(db.committed.ingested.length, 2); // the two distinct events are kept in the landing table
    assert.equal(broker.depth(deadLetterQueueName('contract.terminated')), 0);
  });

  it('contract.settled writes one settlement fact; a replay is ignored', async () => {
    await boot();
    const contractId = randomUUID();
    const settled = event('contract.settled', contractId, { contractId });
    send(settled);
    send(settled);
    send(event('contract.settled', contractId, { contractId })); // new event id, same contract
    await broker.drain();
    const fact = one(db.committed.contractSettlements);
    assert.equal(fact.factId, settled.eventId);
    assert.equal(fact.contractId, contractId);
    assert.equal(fact.settledAt.toISOString(), settled.occurredAt);
  });

  it('restart catch-up: outcomes published while analytics is stopped are all stored once', async () => {
    const first = await boot();
    await first.stopConsuming();
    const breached = randomUUID();
    const mutual = randomUUID();
    const settledId = randomUUID();
    const backlog = [
      terminated(breached, 'TERMINATION_FOR_BREACH', 'SELLER', 'BUYER', 'FUNDING_FAILURE'),
      terminated(mutual, 'MUTUAL_TERMINATION', 'BUYER'),
      event('contract.settled', settledId, { contractId: settledId }),
    ];
    for (const e of backlog) assert.equal(send(e), true);
    for (const e of backlog) send(e); // duplicates in the backlog
    assert.equal(db.committed.contractTerminations.length, 0);

    await boot();
    await broker.drain();

    assert.equal(db.committed.contractTerminations.length, 2);
    assert.equal(db.committed.contractSettlements.length, 1);
    assert.equal(broker.unackedCount(), 0);
  });

  it('an invalid termination (unknown terminationType) goes to the DLQ and writes no fact', async () => {
    await boot();
    const contractId = randomUUID();
    send(event('contract.terminated', contractId, { contractId, terminationType: 'CANCELLED' }));
    await broker.drain();
    assert.equal(db.committed.contractTerminations.length, 0);
    assert.equal(broker.depth(deadLetterQueueName('contract.terminated')), 1);
  });
});
