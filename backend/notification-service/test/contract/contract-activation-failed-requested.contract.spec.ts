/**
 * Contract test: parseContractActivationFailedRequested against
 * `contractActivationFailedRequested` of
 * contracts/events/notification-commands.yaml (D42), read from the
 * repository. If the contract changes, this fails until the code follows it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  CONTRACT_ACTIVATION_FAILED_FIELDS,
  CONTRACT_ACTIVATION_FAILED_REQUESTED,
  parseContractActivationFailedRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import {
  ACTIVATION_NEXT_ACTIONS,
  FAILED_LEGS,
} from '../../src/application/commands';
import {
  activationFailedEnvelope,
  activationFailedPayload,
} from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface ActivationFailedProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & {
      failedLeg: { enum: string[] };
      nextActions: { items: { enum: string[] } };
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
  allOf: [{ $ref: '#/$defs/contractActivationFailedRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseContractActivationFailedRequested(
      Buffer.from(JSON.stringify(message)),
    );
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const activationFailedSchema = (): ActivationFailedProperties => {
  const def = contract.$defs.contractActivationFailedRequested as {
    allOf: Array<{ properties?: ActivationFailedProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('contractActivationFailedRequested declares no payload');
  }
  return own.properties;
};

describe('contractActivationFailedRequested contract (notification-commands.yaml)', () => {
  const schema = activationFailedSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/contractActivationFailedRequested',
    });
    expect(
      contract.$defs.contractActivationFailedRequested['x-consumers'],
    ).toEqual(['notification-service']);
  });

  it('names the eventType and producer the parser and the kind use', () => {
    expect(schema.eventType.const).toBe(CONTRACT_ACTIVATION_FAILED_REQUESTED);
    expect(schema.eventType.const).toBe(
      COMMAND_KINDS.contractActivationFailed.eventType,
    );
    expect(schema.producer.const).toBe('contract-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...CONTRACT_ACTIVATION_FAILED_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...CONTRACT_ACTIVATION_FAILED_FIELDS].sort(),
    );
  });

  it('declares exactly the failed legs and next actions the code accepts', () => {
    expect([...schema.payload.properties.failedLeg.enum].sort()).toEqual(
      [...FAILED_LEGS].sort(),
    );
    expect(
      [...schema.payload.properties.nextActions.items.enum].sort(),
    ).toEqual([...ACTIVATION_NEXT_ACTIONS].sort());
  });

  it('accepts the published message, and so does the parser', () => {
    const message = activationFailedEnvelope();

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each([
    ['failedLeg SELLER', { failedLeg: 'SELLER' }],
    ['failedLeg BOTH', { failedLeg: 'BOTH' }],
    [
      'a contract without a seller deposit',
      { sellerDepositState: 'NOT_REQUIRED' },
    ],
    ['an empty deposit state', { buyerDepositState: '' }],
    ['a deposit state the email has no words for', { buyerDepositState: 'x' }],
    ['no next action', { nextActions: [] }],
    [
      'a repeated next action',
      { nextActions: ['RETRY_DEPOSIT_LOCK', 'RETRY_DEPOSIT_LOCK'] },
    ],
    [
      'an OPERATOR recipient without a platform id',
      {
        recipients: [
          { userId: null, email: 'ops@activation.test', role: 'OPERATOR' },
        ],
      },
    ],
    [
      'an instant with an offset',
      { oneSidedCancelAllowedFrom: '2026-11-14T00:00:00+07:00' },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = activationFailedEnvelope({
      payload: activationFailedPayload(patch),
    });

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  it.each([
    [
      'an unknown payload field',
      { payload: activationFailedPayload({ reason: 'BUYER: no funds' }) },
    ],
    ['no recipients', { payload: activationFailedPayload({ recipients: [] }) }],
    [
      'a failedLeg outside the enum',
      { payload: activationFailedPayload({ failedLeg: 'NONE' }) },
    ],
    [
      'a lower-case failedLeg',
      { payload: activationFailedPayload({ failedLeg: 'buyer' }) },
    ],
    [
      'a missing failedLeg',
      { payload: activationFailedPayload({ failedLeg: undefined }) },
    ],
    [
      'a null deposit state',
      { payload: activationFailedPayload({ sellerDepositState: null }) },
    ],
    [
      'a numeric deposit state',
      { payload: activationFailedPayload({ buyerDepositState: 0 }) },
    ],
    [
      'a missing deposit state',
      { payload: activationFailedPayload({ sellerDepositState: undefined }) },
    ],
    [
      'a next action outside the enum',
      { payload: activationFailedPayload({ nextActions: ['MARK_FAILED'] }) },
    ],
    [
      'next actions that are not an array',
      {
        payload: activationFailedPayload({ nextActions: 'RETRY_DEPOSIT_LOCK' }),
      },
    ],
    [
      'missing next actions',
      { payload: activationFailedPayload({ nextActions: undefined }) },
    ],
    [
      'a date without time',
      {
        payload: activationFailedPayload({
          oneSidedCancelAllowedFrom: '2026-11-14',
        }),
      },
    ],
    [
      'a contractId that is not a UUID',
      { payload: activationFailedPayload({ contractId: 'contract-1' }) },
    ],
    ['another producer', { producer: 'escrow-service' }],
    [
      'the activation-cancel command eventType',
      { eventType: 'notification.activation_cancel_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    // JSON drops undefined, so a field set to undefined is a missing field.
    const message = JSON.parse(
      JSON.stringify(activationFailedEnvelope(overrides)),
    ) as Record<string, unknown>;

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
