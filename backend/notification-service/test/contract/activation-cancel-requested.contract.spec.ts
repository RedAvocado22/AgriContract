/**
 * Contract test: parseActivationCancelRequested against
 * `activationCancelRequested` of contracts/events/notification-commands.yaml
 * (D42), read from the repository. If the contract changes, this fails until
 * the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  ACTIVATION_CANCEL_FIELDS,
  ACTIVATION_CANCEL_REQUESTED,
  parseActivationCancelRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import { CANCEL_REQUESTER_ROLES } from '../../src/application/commands';
import { cancelEnvelope, cancelPayload } from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface CancelProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & {
      requestedByRole: { enum: string[] };
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
  allOf: [{ $ref: '#/$defs/activationCancelRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseActivationCancelRequested(Buffer.from(JSON.stringify(message)));
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const cancelSchema = (): CancelProperties => {
  const def = contract.$defs.activationCancelRequested as {
    allOf: Array<{ properties?: CancelProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('activationCancelRequested declares no payload');
  }
  return own.properties;
};

describe('activationCancelRequested contract (notification-commands.yaml)', () => {
  const schema = cancelSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/activationCancelRequested',
    });
    expect(contract.$defs.activationCancelRequested['x-consumers']).toEqual([
      'notification-service',
    ]);
  });

  it('names the eventType and producer the parser and the kind use', () => {
    expect(schema.eventType.const).toBe(ACTIVATION_CANCEL_REQUESTED);
    expect(schema.eventType.const).toBe(
      COMMAND_KINDS.activationCancelRequested.eventType,
    );
    expect(schema.producer.const).toBe('contract-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...ACTIVATION_CANCEL_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...ACTIVATION_CANCEL_FIELDS].sort(),
    );
  });

  it('declares exactly the requester roles the code accepts', () => {
    expect([...schema.payload.properties.requestedByRole.enum].sort()).toEqual(
      [...CANCEL_REQUESTER_ROLES].sort(),
    );
  });

  it('accepts the published message, and so does the parser', () => {
    const message = cancelEnvelope();

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each([
    ['a seller request', { requestedByRole: 'SELLER' }],
    [
      'an OPERATOR recipient without a platform id',
      {
        recipients: [
          { userId: null, email: 'ops@cancel.test', role: 'OPERATOR' },
        ],
      },
    ],
    [
      'an instant with an offset',
      { oneSidedCancelAllowedFrom: '2026-11-14T10:00:00+07:00' },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = cancelEnvelope({ payload: cancelPayload(patch) });

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  it.each([
    ['an unknown payload field', { payload: cancelPayload({ trigger: 'x' }) }],
    ['no recipients', { payload: cancelPayload({ recipients: [] }) }],
    [
      'a requester outside the enum',
      { payload: cancelPayload({ requestedByRole: 'OPERATOR' }) },
    ],
    [
      'a lower-case requester',
      { payload: cancelPayload({ requestedByRole: 'buyer' }) },
    ],
    [
      'a missing requester',
      { payload: cancelPayload({ requestedByRole: undefined }) },
    ],
    [
      'a date without time',
      { payload: cancelPayload({ oneSidedCancelAllowedFrom: '2026-11-14' }) },
    ],
    [
      'a contractId that is not a UUID',
      { payload: cancelPayload({ contractId: 'contract-1' }) },
    ],
    ['another producer', { producer: 'escrow-service' }],
    [
      'the anchor command eventType',
      { eventType: 'notification.contract_anchor_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    // JSON drops undefined, so a field set to undefined is a missing field.
    const message = JSON.parse(
      JSON.stringify(cancelEnvelope(overrides)),
    ) as Record<string, unknown>;

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
