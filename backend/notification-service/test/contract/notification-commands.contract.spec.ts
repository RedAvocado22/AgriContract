/**
 * Contract test: parseContractAnchorRequested against `contractAnchorRequested`
 * of contracts/events/notification-commands.yaml, read from the repository
 * (like the Java services read ../../contracts). If the contract changes, this
 * fails until the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  CONTRACT_ANCHOR_FIELDS,
  CONTRACT_ANCHOR_REQUESTED,
  parseContractAnchorRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import { RECIPIENT_ROLES } from '../../src/application/commands';
import { anchorEnvelope, anchorPayload } from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

const contract = load(readFileSync(CONTRACT, 'utf8')) as Schema;

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
// The whole document, so $ref and $defs resolve as they do for the publisher.
const contractAccepts = ajv.compile({
  ...contract,
  oneOf: undefined,
  allOf: [{ $ref: '#/$defs/contractAnchorRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseContractAnchorRequested(Buffer.from(JSON.stringify(message)));
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

interface AnchorProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: { required: string[]; properties: Record<string, unknown> };
}

/** The command's own `payload` schema, for the field-level assertions. */
const anchorSchema = (): AnchorProperties => {
  const def = contract.$defs.contractAnchorRequested as {
    allOf: Array<{ properties?: AnchorProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('contractAnchorRequested declares no payload');
  }
  return own.properties;
};

describe('contractAnchorRequested contract (notification-commands.yaml)', () => {
  const schema = anchorSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/contractAnchorRequested',
    });
    expect(contract.$defs.contractAnchorRequested['x-consumers']).toEqual([
      'notification-service',
    ]);
  });

  it('names the eventType and producer the parser and the kind use', () => {
    expect(schema.eventType.const).toBe(CONTRACT_ANCHOR_REQUESTED);
    expect(schema.eventType.const).toBe(COMMAND_KINDS.contractAnchor.eventType);
    expect(schema.producer.const).toBe('contract-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...CONTRACT_ANCHOR_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...CONTRACT_ANCHOR_FIELDS].sort(),
    );
  });

  it('declares exactly the recipient roles the code accepts', () => {
    const roles = (
      contract.$defs.recipients as {
        items: { properties: { role: { enum: string[] } } };
      }
    ).items.properties.role.enum;

    expect([...roles].sort()).toEqual([...RECIPIENT_ROLES].sort());
  });

  it('accepts the published message, and so does the parser', () => {
    const message = anchorEnvelope();

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions matter, for different reasons. A message the contract
   * accepts but the parser rejects would dead-letter a valid command; one the
   * parser accepts but the contract rejects would send an email from a payload
   * no publisher promised.
   */
  it.each([
    ['a recipient without a platform id', { userId: null }],
    [
      'a second recipient of the same contract',
      { userId: '0a1b2c3d-4e5f-4a6b-8c9d-0e1f2a3b4c5d' },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = anchorEnvelope({
      payload: anchorPayload({
        recipients: [
          { ...patch, email: 'extra@anchor.test', role: 'OPERATOR' },
        ],
      }),
    });

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  it.each([
    ['an unknown payload field', { payload: anchorPayload({ otsProof: 'x' }) }],
    ['no recipients', { payload: anchorPayload({ recipients: [] }) }],
    [
      'an uppercase hash',
      { payload: anchorPayload({ signedContentHash: 'A'.repeat(64) }) },
    ],
    [
      'a contractId that is not a UUID',
      { payload: anchorPayload({ contractId: 'contract-1' }) },
    ],
    [
      'a role outside the enum',
      {
        payload: anchorPayload({
          recipients: [{ userId: null, email: 'b@x.test', role: 'BANK' }],
        }),
      },
    ],
    ['another producer', { producer: 'audit-service' }],
    ['another command eventType', { eventType: 'notification.x_requested' }],
    ['a missing correlationId', { correlationId: undefined }],
    ['an envelope subject', { subject: { type: 'CONTRACT', id: 'c-1' } }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = anchorEnvelope(overrides);
    if ('correlationId' in overrides) delete message.correlationId;

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
