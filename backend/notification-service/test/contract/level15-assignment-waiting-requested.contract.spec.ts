/**
 * Contract test: parseLevel15AssignmentWaitingRequested against
 * `level15AssignmentWaitingRequested` of
 * contracts/events/notification-commands.yaml, read from the repository.
 * If the contract changes, this fails until the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  LEVEL15_ASSIGNMENT_WAITING_FIELDS,
  LEVEL15_ASSIGNMENT_WAITING_REQUESTED,
  parseLevel15AssignmentWaitingRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import {
  LEVEL15_WAITING_REASONS,
  LEVEL15_WAITING_STAGES,
} from '../../src/application/commands';
import { waitingEnvelope, waitingPayload } from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface WaitingProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & {
      reason: { enum: string[] };
      stage: { enum: string[] };
    };
  };
}

const contract = load(readFileSync(CONTRACT, 'utf8')) as Schema;

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
// The whole document, so $ref and $defs resolve as they do for the publisher.
const contractAccepts = ajv.compile({
  ...contract,
  oneOf: undefined,
  allOf: [{ $ref: '#/$defs/level15AssignmentWaitingRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseLevel15AssignmentWaitingRequested(
      Buffer.from(JSON.stringify(message)),
    );
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const waitingSchema = (): WaitingProperties => {
  const def = contract.$defs.level15AssignmentWaitingRequested as {
    allOf: Array<{ properties?: WaitingProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('level15AssignmentWaitingRequested declares no payload');
  }
  return own.properties;
};

/** JSON drops undefined, so a field set to undefined is a missing field. */
const sent = (message: Record<string, unknown>): Record<string, unknown> =>
  JSON.parse(JSON.stringify(message)) as Record<string, unknown>;

describe('level15AssignmentWaitingRequested contract (notification-commands.yaml)', () => {
  const schema = waitingSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/level15AssignmentWaitingRequested',
    });
    expect(
      contract.$defs.level15AssignmentWaitingRequested['x-consumers'],
    ).toEqual(['notification-service']);
  });

  it('names the eventType and producer the parser and the kinds use', () => {
    expect(schema.eventType.const).toBe(LEVEL15_ASSIGNMENT_WAITING_REQUESTED);
    for (const kind of [
      COMMAND_KINDS.level15WaitingStarted,
      COMMAND_KINDS.level15StillWaiting,
      COMMAND_KINDS.level15AssignedAfterWait,
    ]) {
      expect(kind.eventType).toBe(schema.eventType.const);
    }
    expect(schema.producer.const).toBe('inspection-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...LEVEL15_ASSIGNMENT_WAITING_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...LEVEL15_ASSIGNMENT_WAITING_FIELDS].sort(),
    );
  });

  it('declares exactly the reasons and stages the code accepts', () => {
    expect([...schema.payload.properties.reason.enum].sort()).toEqual(
      [...LEVEL15_WAITING_REASONS].sort(),
    );
    expect([...schema.payload.properties.stage.enum].sort()).toEqual(
      [...LEVEL15_WAITING_STAGES].sort(),
    );
  });

  it.each([
    ['the start of a wait, as published', {}],
    ['a reminder', { stage: 'STILL_WAITING' }],
    ['an assignment', { stage: 'ASSIGNED', reason: 'ALL_EXCLUDED' }],
    [
      'an instant with an offset',
      { waitingSince: '2026-11-16T10:00:00+07:00' },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = sent(waitingEnvelope({ payload: waitingPayload(patch) }));

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each([
    [
      'an inspector in the payload',
      { payload: waitingPayload({ inspectorId: 'x' }) },
    ],
    ['another stage', { payload: waitingPayload({ stage: 'CANCELLED' }) }],
    [
      'a lower-case reason',
      { payload: waitingPayload({ reason: 'no_eligible' }) },
    ],
    ['a missing stage', { payload: waitingPayload({ stage: undefined }) }],
    [
      'a missing waitingSince',
      { payload: waitingPayload({ waitingSince: undefined }) },
    ],
    [
      'a date without time',
      { payload: waitingPayload({ waitingSince: '2026-11-16' }) },
    ],
    [
      'a milestoneId that is not a UUID',
      { payload: waitingPayload({ milestoneId: 'm-1' }) },
    ],
    ['another producer', { producer: 'contract-service' }],
    [
      'the lock-changed eventType',
      { eventType: 'notification.user_lock_changed_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = sent(waitingEnvelope(overrides));

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
