/**
 * Contract test: parseUserLockChangedRequested against `userLockChangedRequested`
 * of contracts/events/notification-commands.yaml, read from the repository.
 * If the contract changes, this fails until the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  USER_LOCK_CHANGED_FIELDS,
  USER_LOCK_CHANGED_REQUESTED,
  USER_LOCK_CHANGED_REQUIRED,
  parseUserLockChangedRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import { LOCK_ACTIONS } from '../../src/application/commands';
import { lockEnvelope, lockPayload } from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface LockProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & { action: { enum: string[] } };
  };
}

const contract = load(readFileSync(CONTRACT, 'utf8')) as Schema;

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
// The whole document, so $ref and $defs resolve as they do for the publisher.
const contractAccepts = ajv.compile({
  ...contract,
  oneOf: undefined,
  allOf: [{ $ref: '#/$defs/userLockChangedRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseUserLockChangedRequested(Buffer.from(JSON.stringify(message)));
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const lockSchema = (): LockProperties => {
  const def = contract.$defs.userLockChangedRequested as {
    allOf: Array<{ properties?: LockProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('userLockChangedRequested declares no payload');
  }
  return own.properties;
};

/** JSON drops undefined, so a field set to undefined is a missing field. */
const sent = (message: Record<string, unknown>): Record<string, unknown> =>
  JSON.parse(JSON.stringify(message)) as Record<string, unknown>;

describe('userLockChangedRequested contract (notification-commands.yaml)', () => {
  const schema = lockSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/userLockChangedRequested',
    });
    expect(contract.$defs.userLockChangedRequested['x-consumers']).toEqual([
      'notification-service',
    ]);
  });

  it('names the eventType and producer the parser and the kind use', () => {
    expect(schema.eventType.const).toBe(USER_LOCK_CHANGED_REQUESTED);
    expect(schema.eventType.const).toBe(
      COMMAND_KINDS.userLockChanged.eventType,
    );
    expect(schema.producer.const).toBe('user-service');
  });

  it('declares exactly the payload fields the parser reads, and requires all but effectiveUntil', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...USER_LOCK_CHANGED_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...USER_LOCK_CHANGED_REQUIRED].sort(),
    );
  });

  it('declares exactly the actions the code accepts', () => {
    expect([...schema.payload.properties.action.enum].sort()).toEqual(
      [...LOCK_ACTIONS].sort(),
    );
  });

  it.each([
    ['a lock, as published', {}],
    [
      'an unlock with no lock left',
      { action: 'UNLOCKED', effectiveUntil: null },
    ],
    [
      'an unlock with another lock on',
      { action: 'UNLOCKED', effectiveUntil: '2026-11-20T00:00:00Z' },
    ],
    ['no effectiveUntil at all', { effectiveUntil: undefined }],
    ['an empty reason code', { reasonCode: '' }],
    [
      'an instant with an offset',
      { effectiveUntil: '2026-12-11T11:00:00+07:00' },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = sent(lockEnvelope({ payload: lockPayload(patch) }));

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each([
    ['an unknown payload field', { payload: lockPayload({ lockRevision: 3 }) }],
    ['another action', { payload: lockPayload({ action: 'SUSPENDED' }) }],
    ['a lower-case action', { payload: lockPayload({ action: 'locked' }) }],
    ['a null reason code', { payload: lockPayload({ reasonCode: null }) }],
    [
      'a missing reason code',
      { payload: lockPayload({ reasonCode: undefined }) },
    ],
    [
      'a missing address',
      { payload: lockPayload({ recipientEmail: undefined }) },
    ],
    [
      'a userId that is not a UUID',
      { payload: lockPayload({ userId: 'u-1' }) },
    ],
    [
      'a date without time',
      { payload: lockPayload({ effectiveUntil: '2026-12-11' }) },
    ],
    ['another producer', { producer: 'reputation-service' }],
    [
      'the KYC command eventType',
      { eventType: 'notification.user_kyc_result_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = sent(lockEnvelope(overrides));

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
