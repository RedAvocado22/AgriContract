/**
 * Both projections that touch a milestone must take the per-milestone lock BEFORE they read anything
 * (leader review of #173: settlement vs force-majeure race). The real lock is exercised against MySQL in
 * mysql-projections.test.ts; here the order of repository calls is pinned with the in-memory fake.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import type { IngestEventCommand } from '../src/application/port';
import { FlagForceMajeure } from '../src/application/usecase/flagForceMajeure';
import { RecordMilestoneOutcome } from '../src/application/usecase/recordMilestoneOutcome';
import { InMemoryDb, InMemoryMilestoneFacts, event, type FakeTx } from './fakes';
import { deepEqual } from './deepEqual';

function command(eventType: string, payload: Record<string, unknown>): IngestEventCommand {
  const contractId = randomUUID();
  const e = event(eventType, contractId, { contractId, ...payload });
  return {
    eventId: e.eventId,
    eventType: e.eventType,
    eventVersion: e.eventVersion,
    producer: e.producer,
    aggregateId: e.aggregateId,
    occurredAt: new Date(e.occurredAt),
    correlationId: null,
    causationId: null,
    payload: e.payload as Record<string, unknown>,
  };
}

/** The fake with every repository method recorded, in order. */
class RecordingFacts extends InMemoryMilestoneFacts {
  override async insertIfAbsent(...args: Parameters<InMemoryMilestoneFacts['insertIfAbsent']>) {
    this.calls.push('insertIfAbsent');
    return super.insertIfAbsent(...args);
  }
  override async markForceMajeure(...args: Parameters<InMemoryMilestoneFacts['markForceMajeure']>) {
    this.calls.push('markForceMajeure');
    return super.markForceMajeure(...args);
  }
  override async addPendingForceMajeure(...args: Parameters<InMemoryMilestoneFacts['addPendingForceMajeure']>) {
    this.calls.push('addPending');
    return super.addPendingForceMajeure(...args);
  }
  override async hasPendingForceMajeure(...args: Parameters<InMemoryMilestoneFacts['hasPendingForceMajeure']>) {
    this.calls.push('hasPending');
    return super.hasPendingForceMajeure(...args);
  }
  override async deletePendingForceMajeure(...args: Parameters<InMemoryMilestoneFacts['deletePendingForceMajeure']>) {
    this.calls.push('deletePending');
    return super.deletePendingForceMajeure(...args);
  }
}

const run = (work: (tx: FakeTx) => Promise<void>) => new InMemoryDb().inTransaction(work);

describe('per-milestone lock order', () => {
  it('settled / cancelled take the lock before reading the staged flag', async () => {
    for (const make of [RecordMilestoneOutcome.settled, RecordMilestoneOutcome.cancelledWithPenalty]) {
      const facts = new RecordingFacts();
      const milestoneId = randomUUID();
      const handler = make(facts);
      await run((tx) => handler.apply(command(handler.eventType, { milestoneId }), tx));
      assert.equal(facts.calls[0], `lock:${milestoneId}`, handler.eventType);
      assert.ok(facts.calls.indexOf('hasPending') > 0);
    }
  });

  it('force majeure takes the lock before looking for the fact row', async () => {
    const facts = new RecordingFacts();
    const milestoneId = randomUUID();
    await run((tx) => new FlagForceMajeure(facts).apply(command('milestone.force_majeure_resolved', { milestoneId, decision: 'APPROVED' }), tx));
    assert.equal(facts.calls[0], `lock:${milestoneId}`);
    deepEqual(facts.calls.slice(1), ['markForceMajeure', 'addPending']);
  });

  it('a rejected decision touches nothing, not even the lock', async () => {
    const facts = new RecordingFacts();
    await run((tx) => new FlagForceMajeure(facts).apply(command('milestone.force_majeure_resolved', { milestoneId: randomUUID(), decision: 'REJECTED' }), tx));
    deepEqual(facts.calls, []);
  });
});
