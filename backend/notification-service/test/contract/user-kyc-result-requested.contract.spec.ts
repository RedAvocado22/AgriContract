/**
 * Contract test: parseUserKycResultRequested against `userKycResultRequested`
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
  USER_KYC_RESULT_FIELDS,
  USER_KYC_RESULT_REQUESTED,
  USER_KYC_RESULT_REQUIRED,
  parseUserKycResultRequested,
} from '../../src/infrastructure/messaging/notification-command.parsers';
import { COMMAND_KINDS } from '../../src/application/notification-command-kinds';
import { KYC_RESULTS } from '../../src/application/commands';
import {
  kycEnvelope,
  kycPayload,
  kycVerifiedPayload,
} from '../support/commands';

const CONTRACT = join(
  __dirname,
  '../../../../contracts/events/notification-commands.yaml',
);

interface Schema {
  $defs: Record<string, Record<string, unknown>>;
  oneOf: Array<{ $ref: string }>;
}

interface KycProperties {
  eventType: { const: string };
  producer: { const: string };
  payload: {
    required: string[];
    properties: Record<string, unknown> & { result: { enum: string[] } };
  };
}

const contract = load(readFileSync(CONTRACT, 'utf8')) as Schema;

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
// The whole document, so $ref and $defs resolve as they do for the publisher.
const contractAccepts = ajv.compile({
  ...contract,
  oneOf: undefined,
  allOf: [{ $ref: '#/$defs/userKycResultRequested' }],
});

const codeAccepts = (message: unknown): boolean => {
  try {
    parseUserKycResultRequested(Buffer.from(JSON.stringify(message)));
    return true;
  } catch (err) {
    if (err instanceof NonRetryableError) return false;
    throw err;
  }
};

/** The command's own properties, for the field-level assertions. */
const kycSchema = (): KycProperties => {
  const def = contract.$defs.userKycResultRequested as {
    allOf: Array<{ properties?: KycProperties }>;
  };
  const own = def.allOf.find((part) => part.properties?.payload);
  if (!own?.properties) {
    throw new Error('userKycResultRequested declares no payload');
  }
  return own.properties;
};

/** JSON drops undefined, so a field set to undefined is a missing field. */
const sent = (message: Record<string, unknown>): Record<string, unknown> =>
  JSON.parse(JSON.stringify(message)) as Record<string, unknown>;

describe('userKycResultRequested contract (notification-commands.yaml)', () => {
  const schema = kycSchema();

  it('is a command of the contract, consumed by this service', () => {
    expect(contract.oneOf).toContainEqual({
      $ref: '#/$defs/userKycResultRequested',
    });
    expect(contract.$defs.userKycResultRequested['x-consumers']).toEqual([
      'notification-service',
    ]);
  });

  it('names the eventType and producer the parser and the kind use', () => {
    expect(schema.eventType.const).toBe(USER_KYC_RESULT_REQUESTED);
    expect(schema.eventType.const).toBe(COMMAND_KINDS.userKycResult.eventType);
    expect(schema.producer.const).toBe('user-service');
  });

  it('declares exactly the payload fields the parser reads, and requires all but reason', () => {
    expect(Object.keys(schema.payload.properties).sort()).toEqual(
      [...USER_KYC_RESULT_FIELDS].sort(),
    );
    expect([...schema.payload.required].sort()).toEqual(
      [...USER_KYC_RESULT_REQUIRED].sort(),
    );
  });

  it('declares exactly the results the code accepts', () => {
    expect([...schema.payload.properties.result.enum].sort()).toEqual(
      [...KYC_RESULTS].sort(),
    );
  });

  it.each([
    ['a rejection, as published', kycEnvelope()],
    [
      'a verification, as published (no reason)',
      kycEnvelope({ payload: kycVerifiedPayload() }),
    ],
  ])('accepts %s, and so does the parser', (_case, message) => {
    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  /*
   * Both directions: a message the contract accepts but the parser rejects
   * would dead-letter a valid command; one the parser accepts but the
   * contract rejects would send an email no publisher promised.
   */
  it.each([
    ['a rejection without a reason', { reason: undefined }],
    ['an empty reason', { reason: '' }],
    ['an instant with an offset', { decidedAt: '2026-11-11T09:30:00+07:00' }],
    ['a name in Vietnamese', { organizationName: 'Hợp tác xã Cà phê Đắk Lắk' }],
  ])('accepts %s on both sides', (_case, patch) => {
    const message = sent(kycEnvelope({ payload: kycPayload(patch) }));

    expect(contractAccepts(message)).toBe(true);
    expect(codeAccepts(message)).toBe(true);
  });

  it.each([
    ['an unknown payload field', { payload: kycPayload({ actorId: 'x' }) }],
    ['a null reason', { payload: kycPayload({ reason: null }) }],
    ['a PENDING result', { payload: kycPayload({ result: 'PENDING' }) }],
    ['a lower-case result', { payload: kycPayload({ result: 'verified' }) }],
    [
      'a missing organisation name',
      { payload: kycPayload({ organizationName: undefined }) },
    ],
    [
      'a missing address',
      { payload: kycPayload({ recipientEmail: undefined }) },
    ],
    ['a userId that is not a UUID', { payload: kycPayload({ userId: 'u-1' }) }],
    [
      'a date without time',
      { payload: kycPayload({ decidedAt: '2026-11-11' }) },
    ],
    ['another producer', { producer: 'contract-service' }],
    [
      'the lock command eventType',
      { eventType: 'notification.user_lock_changed_requested' },
    ],
    ['a missing correlationId', { correlationId: undefined }],
  ])('rejects %s on both sides', (_case, overrides) => {
    const message = sent(kycEnvelope(overrides));

    expect(contractAccepts(message)).toBe(false);
    expect(codeAccepts(message)).toBe(false);
  });
});
