/**
 * Contract test: parseLevel15InspectorAssignmentRequested against
 * `level15InspectorAssignmentRequested` of
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
  LEVEL15_INSPECTOR_ASSIGNMENT_FIELDS,
  LEVEL15_INSPECTOR_ASSIGNMENT_REQUESTED,
  parseLevel15InspectorAssignmentRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import { LEVEL15_INSPECTOR_STAGES } from '../../src/application/commands';
import {
  INSPECTOR_RECIPIENT,
  inspectorEnvelope,
  inspectorPayload,
} from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface InspectorProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & {
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
  allOf: [{ $ref: '#/$defs/level15InspectorAssignmentRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseLevel15InspectorAssignmentRequested(
      Buffer.from(JSON.stringify(message)),
    );
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const inspectorSchema = (): InspectorProperties => {
  const def = contract.$defs.level15InspectorAssignmentRequested as {
    allOf: Array<{ properties?: InspectorProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('level15InspectorAssignmentRequested declares no payload');
  }
  return own.properties;
};

/** JSON drops undefined, so a field set to undefined is a missing field. */
const sent = (message: Record<string, unknown>): Record<string, unknown> =>
  JSON.parse(JSON.stringify(message)) as Record<string, unknown>;

const OTHER = { ...INSPECTOR_RECIPIENT, email: 'other@inspect.test' };

describe('level15InspectorAssignmentRequested contract (notification-commands.yaml)', () => {
  const schema = inspectorSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/level15InspectorAssignmentRequested',
    });
    expect(
      contract.$defs.level15InspectorAssignmentRequested['x-consumers'],
    ).toEqual(['notification-service']);
  });

  it('names the eventType and producer the parser and the kinds use', () => {
    expect(schema.eventType.const).toBe(LEVEL15_INSPECTOR_ASSIGNMENT_REQUESTED);
    for (const kind of [
      COMMAND_KINDS.level15InspectorAssigned,
      COMMAND_KINDS.level15InspectorCancelled,
    ]) {
      expect(kind.eventType).toBe(schema.eventType.const);
    }
    expect(schema.producer.const).toBe('inspection-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...LEVEL15_INSPECTOR_ASSIGNMENT_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...LEVEL15_INSPECTOR_ASSIGNMENT_FIELDS].sort(),
    );
  });

  it('declares exactly the stages the code accepts', () => {
    expect([...schema.payload.properties.stage.enum].sort()).toEqual(
      [...LEVEL15_INSPECTOR_STAGES].sort(),
    );
  });

  it.each([
    ['an assignment, as published', {}],
    ['a cancellation', { stage: 'CANCELLED' }],
    [
      'an inspector without a platform id',
      { recipients: [{ ...INSPECTOR_RECIPIENT, userId: null }] },
    ],
    [
      'instants with an offset',
      {
        reportDueAt: '2026-11-25T17:00:00+07:00',
        freeCancelUntil: '2026-11-21T09:00:00+07:00',
      },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = sent(
      inspectorEnvelope({ payload: inspectorPayload(patch) }),
    );

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
      'a second recipient',
      {
        payload: inspectorPayload({ recipients: [INSPECTOR_RECIPIENT, OTHER] }),
      },
    ],
    [
      'a BUYER recipient',
      {
        payload: inspectorPayload({
          recipients: [{ ...INSPECTOR_RECIPIENT, role: 'BUYER' }],
        }),
      },
    ],
    [
      'an OPERATOR recipient',
      {
        payload: inspectorPayload({
          recipients: [{ ...INSPECTOR_RECIPIENT, role: 'OPERATOR' }],
        }),
      },
    ],
    ['no recipient', { payload: inspectorPayload({ recipients: [] }) }],
    [
      'a waiting stage',
      { payload: inspectorPayload({ stage: 'STILL_WAITING' }) },
    ],
    [
      'a lower-case stage',
      { payload: inspectorPayload({ stage: 'assigned' }) },
    ],
    [
      'a missing commissionId',
      { payload: inspectorPayload({ commissionId: undefined }) },
    ],
    [
      'a missing freeCancelUntil',
      { payload: inspectorPayload({ freeCancelUntil: undefined }) },
    ],
    [
      'a date without time',
      { payload: inspectorPayload({ reportDueAt: '2026-11-25' }) },
    ],
    [
      'a commissionId that is not a UUID',
      { payload: inspectorPayload({ commissionId: 'c-1' }) },
    ],
    [
      'an extra payload field',
      { payload: inspectorPayload({ inspectorName: 'x' }) },
    ],
    ['another producer', { producer: 'contract-service' }],
    [
      'the waiting eventType',
      { eventType: 'notification.level1_5_assignment_waiting_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = sent(inspectorEnvelope(overrides));

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
