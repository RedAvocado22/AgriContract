import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { MysqlConnectionLike } from '@agricontract/eventing';
import { toIngestCommand } from '../src/infrastructure/messaging/commandMapper.js';
import { MysqlIngestedEventStore, toMysqlDateTime } from '../src/infrastructure/persistence/ingestedEventStore.js';
import { validEvent } from './fixtures.js';

function fakeTx() {
  const calls: Array<{ sql: string; values: unknown[] }> = [];
  const tx = {
    async query(sql: string, values: unknown[] = []) {
      calls.push({ sql, values });
      return [[], []] as [unknown, unknown];
    },
  } as unknown as MysqlConnectionLike;
  return { tx, calls };
}

describe('toIngestCommand', () => {
  it('carries the envelope identity, UTC time and tracing ids', () => {
    const env = validEvent('contract.settled', 'c-1');
    const command = toIngestCommand(env);
    assert.equal(command.eventId, env.eventId);
    assert.equal(command.eventType, 'contract.settled');
    assert.equal(command.producer, 'contract-service');
    assert.equal(command.aggregateId, 'c-1');
    assert.equal(command.occurredAt.toISOString(), new Date(env.occurredAt).toISOString());
    assert.equal(command.correlationId, 'saga-c-1');
    assert.equal(command.causationId, null);
  });

  it('removes recipient e-mail addresses and keeps userId and role', () => {
    const env = validEvent('milestone.settled', 'm-1');
    const command = toIngestCommand(env);
    const recipients = (command.payload as { recipients: Array<Record<string, unknown>> }).recipients;
    assert.equal(recipients.length, 2);
    for (const r of recipients) assert.deepEqual(Object.keys(r).sort(), ['role', 'userId']);
    assert.doesNotMatch(JSON.stringify(command), /@example\.com/);
  });

  it('does not change the original envelope', () => {
    const env = validEvent('milestone.settled', 'm-1');
    toIngestCommand(env);
    assert.match(JSON.stringify(env.payload), /buyer@example\.com/);
  });

  it('leaves payloads without recipients untouched', () => {
    const env = validEvent('contract.signed', 'c-1');
    assert.deepEqual(toIngestCommand(env).payload, env.payload);
  });
});

describe('MysqlIngestedEventStore', () => {
  it('inserts one row into analytics_ingested_event with every column, inside the given transaction', async () => {
    const { tx, calls } = fakeTx();
    const env = validEvent('contract.signed', 'c-1');
    const command = toIngestCommand(env);
    await new MysqlIngestedEventStore().record(tx, command);
    assert.equal(calls.length, 1);
    assert.match(calls[0]!.sql, /INSERT INTO analytics_ingested_event/);
    assert.doesNotMatch(calls[0]!.sql, /IGNORE|ON DUPLICATE/i, 'a duplicate must surface, dedupe happens before');
    assert.deepEqual(calls[0]!.values, [
      env.eventId,
      'contract.signed',
      1,
      'contract-service',
      'c-1',
      'saga-c-1',
      null,
      toMysqlDateTime(command.occurredAt),
      JSON.stringify(command.payload),
    ]);
  });

  it('formats times as UTC DATETIME(3)', () => {
    assert.equal(toMysqlDateTime(new Date('2026-10-12T05:00:00.123Z')), '2026-10-12 05:00:00.123');
    assert.equal(toMysqlDateTime(new Date('2026-10-12T12:00:00+07:00')), '2026-10-12 05:00:00.000');
  });
});
