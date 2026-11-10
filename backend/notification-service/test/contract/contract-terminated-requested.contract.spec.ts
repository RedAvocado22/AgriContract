/**
 * Contract test: parseContractTerminatedRequested against
 * `contractTerminatedRequested` of contracts/events/notification-commands.yaml,
 * read from the repository. If the contract changes, this fails until the code
 * follows it. Every termination type parses (D43 needs only WITHDRAW_OFFER
 * mailed today, #278 adds the others), so the whole command is checked.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { NonRetryableError } from '@agricontract/eventing';
import { load } from 'js-yaml';
import {
  CONTRACT_TERMINATED_FIELDS,
  CONTRACT_TERMINATED_REQUESTED,
  parseContractTerminatedRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import {
  BREACH_REASON_CODES,
  FUND_TYPES,
  PARTY_ROLES,
  REMEDY_TYPES,
  TERMINATION_REQUESTERS,
  TERMINATION_TYPES,
} from '../../src/application/commands';
import {
  BREACH_TERMINATION,
  terminatedEnvelope,
  terminatedPayload,
} from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

type EnumOf = { enum: string[] };

interface TerminatedProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & {
      terminationType: EnumOf;
      requestedBy: EnumOf;
      finalBreachingRole: { oneOf: Array<Partial<EnumOf>> };
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
  allOf: [{ $ref: '#/$defs/contractTerminatedRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseContractTerminatedRequested(Buffer.from(JSON.stringify(message)));
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

const terminatedSchema = (): TerminatedProperties => {
  const def = contract.$defs.contractTerminatedRequested as {
    allOf: Array<{ properties?: TerminatedProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('contractTerminatedRequested declares no payload');
  }
  return own.properties;
};

const defEnum = (name: string): string[] =>
  [...(contract.$defs[name] as unknown as EnumOf).enum].sort();

const legEnum = (field: string): string[] =>
  [
    ...(contract.$defs.remedyLeg as { properties: Record<string, EnumOf> })
      .properties[field].enum,
  ].sort();

const leg = (BREACH_TERMINATION.remedyLegs as Record<string, unknown>[])[0];

describe('contractTerminatedRequested contract (notification-commands.yaml)', () => {
  const schema = terminatedSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/contractTerminatedRequested',
    });
    expect(contract.$defs.contractTerminatedRequested['x-consumers']).toEqual([
      'notification-service',
    ]);
  });

  it('names the eventType and producer the parser uses', () => {
    expect(schema.eventType.const).toBe(CONTRACT_TERMINATED_REQUESTED);
    expect(schema.producer.const).toBe('contract-service');
  });

  it('declares exactly the payload fields the parser reads, all required', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...CONTRACT_TERMINATED_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...CONTRACT_TERMINATED_FIELDS].sort(),
    );
  });

  it('declares exactly the enum values the code accepts', () => {
    const p = schema.payload.properties;
    expect([...p.terminationType.enum].sort()).toEqual(
      [...TERMINATION_TYPES].sort(),
    );
    expect([...p.requestedBy.enum].sort()).toEqual(
      [...TERMINATION_REQUESTERS].sort(),
    );
    expect(
      [...(p.finalBreachingRole.oneOf.find((o) => o.enum)?.enum ?? [])].sort(),
    ).toEqual([...PARTY_ROLES].sort());
    expect(defEnum('breachReasonCode')).toEqual(
      [...BREACH_REASON_CODES].sort(),
    );
    expect(legEnum('remedyType')).toEqual([...REMEDY_TYPES].sort());
    expect(legEnum('fundType')).toEqual([...FUND_TYPES].sort());
    expect(legEnum('role')).toEqual([...PARTY_ROLES].sort());
  });

  it('accepts the published withdraw-offer message, and so does the parser', () => {
    const message = terminatedEnvelope();

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each<[string, Record<string, unknown>]>([
    ['a seller withdrawal', { requestedBy: 'SELLER' }],
    ['a breach termination with every field filled', BREACH_TERMINATION],
    ...TERMINATION_TYPES.map((t): [string, Record<string, unknown>] => [
      `terminationType ${t}`,
      { terminationType: t },
    ]),
    [
      'a replacement naming both contracts',
      {
        terminationType: 'MUTUAL_REPLACEMENT',
        supersededByContractId: '5f6a7b8c-9d0e-4f1a-8b2c-4d5e6f7a8b9c',
        replacesContractId: '6a7b8c9d-0e1f-4a2b-9c3d-5e6f7a8b9c0d',
      },
    ],
    [
      'the largest amount',
      {
        remedyLegs: [{ ...leg, amount: `${'9'.repeat(16)}.99` }],
      },
    ],
    [
      'an OPERATOR recipient without a platform id',
      {
        recipients: [
          { userId: null, email: 'ops@withdraw.test', role: 'OPERATOR' },
        ],
      },
    ],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = terminatedEnvelope({ payload: terminatedPayload(patch) });

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  it.each([
    ['an unknown payload field', { reason: 'x' }],
    ['no recipients', { recipients: [] }],
    ['a terminationType outside the enum', { terminationType: 'CANCELLED' }],
    ['a lower-case terminationType', { terminationType: 'withdraw_offer' }],
    ['requestedBy OPERATOR', { requestedBy: 'OPERATOR' }],
    ['finalBreachingRole SYSTEM', { finalBreachingRole: 'SYSTEM' }],
    ['a breachReasonCode outside the enum', { breachReasonCode: 'LATE' }],
    ['a remedyDecisionId that is not a UUID', { remedyDecisionId: 'd-1' }],
    ['a missing nullable field', { breachCaseId: undefined }],
    ['affectedMilestoneIds null', { affectedMilestoneIds: null }],
    [
      'a repeated milestone id',
      {
        affectedMilestoneIds: [
          '2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f',
          '2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f',
        ],
      },
    ],
    ['remedyLegs null', { remedyLegs: null }],
    [
      'an amount without decimals',
      { remedyLegs: [{ ...leg, amount: '7126425' }] },
    ],
    [
      'an amount over 19 characters',
      { remedyLegs: [{ ...leg, amount: `${'9'.repeat(17)}.00` }] },
    ],
    [
      'a remedy leg with an extra field',
      { remedyLegs: [{ ...leg, note: 'x' }] },
    ],
    [
      'a remedy leg without its role',
      { remedyLegs: [{ ...leg, role: undefined }] },
    ],
  ])('rejects %s on both sides', (_case, patch) => {
    // JSON drops undefined, so a field set to undefined is a missing field.
    const message = JSON.parse(
      JSON.stringify(terminatedEnvelope({ payload: terminatedPayload(patch) })),
    ) as Record<string, unknown>;

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });

  it.each([
    ['another producer', { producer: 'escrow-service' }],
    [
      'the activation-cancel eventType',
      { eventType: 'notification.activation_cancel_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = JSON.parse(
      JSON.stringify(terminatedEnvelope(overrides)),
    ) as Record<string, unknown>;

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
