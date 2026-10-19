/**
 * Payload validation at the messaging boundary, checked against the frozen contract itself:
 *  - the production rules and golden-flow-events.yaml list the same fields, producers and enum values;
 *  - AJV (JSON Schema 2020-12 + ajv-formats) compiled from the frozen YAML and the production validator must
 *    agree on valid payloads and on hundreds of mutated ones, so a rule that is looser or stricter than the
 *    YAML fails. AJV is the oracle; the tests do not contain their own re-implementation of the schema rules.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import Ajv2020Module from 'ajv/dist/2020.js';
import addFormatsModule from 'ajv-formats';
import yaml from 'js-yaml';
import { INGESTED_EVENT_TYPES } from '../src/domain/ingestedEvents.js';
import { EVENT_CONTRACTS, validateCanonicalEvent, type Rule } from '../src/infrastructure/messaging/eventContracts.js';
import { validEvent, validPayload } from './fixtures.js';

const here = dirname(fileURLToPath(import.meta.url));
const catalog = yaml.load(readFileSync(join(resolve(here, '../../..'), 'contracts/events/golden-flow-events.yaml'), 'utf8')) as {
  $defs: Record<string, any>;
};
const defs = catalog.$defs;

/** golden-flow def name for each event type (the def's own eventType const is the source of truth). */
function defFor(eventType: string): any {
  const found = Object.values(defs).find((d) => d?.allOf?.[1]?.properties?.eventType?.const === eventType);
  assert.ok(found, `${eventType} not found in golden-flow-events.yaml`);
  return found.allOf[1];
}

function resolveRef(ref: string): any {
  assert.match(ref, /^#\/\$defs\//, `unsupported $ref ${ref}`);
  return defs[ref.slice('#/$defs/'.length)];
}

// ---- AJV compiled from the frozen schema ----
const Ajv2020 = ((Ajv2020Module as unknown as { default?: unknown }).default ?? Ajv2020Module) as new (
  opts: object,
) => { compile(schema: object): (data: unknown) => boolean };
const addFormats = ((addFormatsModule as unknown as { default?: unknown }).default ?? addFormatsModule) as (ajv: unknown) => void;
const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const compiled = new WeakMap<object, (data: unknown) => boolean>();
/** True when AJV accepts `v` for the (payload) schema of the YAML; "#/$defs/..." references resolve in the catalog. */
function valid(schema: object, v: unknown): boolean {
  let validate = compiled.get(schema);
  if (!validate) {
    validate = ajv.compile({ $schema: 'https://json-schema.org/draft/2020-12/schema', $defs: defs, ...schema });
    compiled.set(schema, validate);
  }
  return validate(v);
}

// ---- helpers to read enum values out of the YAML ----
function yamlEnum(node: any): string[] | undefined {
  if (node.$ref) return yamlEnum(resolveRef(node.$ref));
  if (node.enum) return [...node.enum];
  if (node.oneOf) {
    const found = node.oneOf.map(yamlEnum).find((e: string[] | undefined) => e);
    return found;
  }
  return undefined;
}
function ruleEnum(rule: Rule): readonly string[] | undefined {
  if (rule.kind === 'enum') return rule.values;
  if (rule.kind === 'nullable') return ruleEnum(rule.rule);
  return undefined;
}

describe('production rules match golden-flow-events.yaml', () => {
  for (const eventType of INGESTED_EVENT_TYPES) {
    describe(eventType, () => {
      const def = defFor(eventType);
      const payloadSchema = def.properties.payload;
      const contract = EVENT_CONTRACTS[eventType];

      it('same producer, same required fields, no extra or missing property', () => {
        assert.equal(contract.producer, def.properties.producer.const);
        assert.deepEqual(Object.keys(contract.payload).sort(), [...payloadSchema.required].sort());
        assert.deepEqual(Object.keys(contract.payload).sort(), Object.keys(payloadSchema.properties).sort());
        assert.equal(payloadSchema.additionalProperties, false);
      });

      it('same enum values for every enum field', () => {
        for (const [field, rule] of Object.entries(contract.payload)) {
          const expected = yamlEnum(payloadSchema.properties[field]);
          const actual = ruleEnum(rule);
          if (expected || actual) assert.deepEqual([...(actual ?? [])].sort(), [...(expected ?? [])].sort(), field);
        }
      });
    });
  }

  it('every ingested event type is covered by a rule set', () => {
    assert.deepEqual(Object.keys(EVENT_CONTRACTS).sort(), [...INGESTED_EVENT_TYPES].sort());
  });
});

describe('validator and YAML schema agree', () => {
  const BAD_VALUES: unknown[] = [
    null, true, 0, -1, 1.5, 1e21, '', ' ', 'x', 'not-a-uuid', '12', '12.5', '12.345', '-1.00', '1e3', '0.00', '99999999999999999999.00',
    '2026-13-45T00:00:00Z', '2026-10-12', '2026-10-12T05:00:00',
    // calendar and clock limits (review finding: Date.parse rolls 2026-02-30 over to 2 March)
    '2026-02-30T00:00:00Z', '2026-04-31T00:00:00Z', '2025-02-29T00:00:00Z', '2024-02-29T00:00:00Z', '2026-00-10T05:00:00Z',
    '2026-10-00T05:00:00Z', '2026-10-12T24:00:00Z', '2026-10-12T05:60:00Z', '2026-10-12T05:00:60Z', '2016-12-31T23:59:60Z',
    '2026-10-12T05:00:00+24:00', '2026-10-12T05:00:00+07:00', '2026-10-12T05:00:00.123Z', '2026-10-12t05:00:00z', 'COFFEE', 'ROBUSTA', 'BUYER', 'VND', [], [null], {}, { userId: 'x' },
  ];

  for (const eventType of INGESTED_EVENT_TYPES) {
    const payloadSchema = defFor(eventType).properties.payload;

    it(`${eventType}: the valid fixture passes both`, () => {
      const payload = validPayload(eventType);
      assert.equal(valid(payloadSchema, payload), true);
      assert.deepEqual(validateCanonicalEvent(validEvent(eventType, 'agg-1', { payload }), eventType), []);
    });

    it(`${eventType}: dropping any field, adding a field or replacing a value gives the same verdict in both`, () => {
      const base = validPayload(eventType);
      const mutants: Array<[string, Record<string, unknown>]> = [['empty payload', {}], ['unknown field', { ...base, extra: 'x' }]];
      // names that exist on every object (review finding: the `in` operator accepted them as known fields)
      for (const name of ['constructor', 'toString', 'hasOwnProperty', 'valueOf', '__proto__']) {
        mutants.push([`extra field ${name}`, JSON.parse(`${JSON.stringify(base).slice(0, -1)},"${name}":"x"}`)]);
      }
      for (const field of Object.keys(base)) {
        const { [field]: _dropped, ...without } = base;
        mutants.push([`missing ${field}`, without]);
        for (const bad of BAD_VALUES) mutants.push([`${field} = ${JSON.stringify(bad)}`, { ...base, [field]: bad }]);
      }
      let rejected = 0;
      for (const [label, payload] of mutants) {
        const byYaml = valid(payloadSchema, payload);
        const byService = validateCanonicalEvent(validEvent(eventType, 'agg-1', { payload }), eventType).length === 0;
        assert.equal(byService, byYaml, `${eventType}: ${label} (yaml says ${byYaml ? 'valid' : 'invalid'})`);
        if (!byYaml) rejected += 1;
      }
      assert.ok(rejected > 10, 'the mutations must contain real rejections');
    });
  }

  it('recipients: exactly userId, email and role, role limited to BUYER/SELLER', () => {
    const base = validPayload('milestone.settled');
    const recipient = { userId: '7d4c0b52-7f2f-4a1a-9d8e-0f6a1f6f7c11', email: 'a@b.c', role: 'BUYER' };
    for (const recipients of [[{ ...recipient, role: 'ADMIN' }], [{ ...recipient, extra: 1 }], [{ userId: recipient.userId, role: 'BUYER' }], 'x', [1]]) {
      const payload = { ...base, recipients };
      assert.equal(validateCanonicalEvent(validEvent('milestone.settled', 'a', { payload }), 'milestone.settled').length > 0, true);
      assert.equal(valid(defFor('milestone.settled').properties.payload, payload), false);
    }
    assert.deepEqual(validateCanonicalEvent(validEvent('milestone.settled', 'a', { payload: { ...base, recipients: [] } }), 'milestone.settled'), []);
  });
});

describe('what the production validator rejects (review finding)', () => {
  it('a date that does not exist in the calendar is rejected (signedAt 2026-02-30)', () => {
    const payload = { ...validPayload('contract.signed'), signedAt: '2026-02-30T00:00:00Z' };
    assert.deepEqual(validateCanonicalEvent(validEvent('contract.signed', 'c-1', { payload }), 'contract.signed'), [
      'payload.signedAt: must be an RFC 3339 date-time with a time zone',
    ]);
    assert.equal(valid(defFor('contract.signed').properties.payload, payload), false);
  });

  it('a field that only exists on every object ("constructor") is not a known field', () => {
    const payload = JSON.parse(`${JSON.stringify(validPayload('contract.settled')).slice(0, -1)},"constructor":"x"}`);
    assert.deepEqual(validateCanonicalEvent(validEvent('contract.settled', 'c-1', { payload }), 'contract.settled'), [
      'payload.constructor: is not allowed',
    ]);
    assert.equal(valid(defFor('contract.settled').properties.payload, payload), false);
  });

  it('an envelope-valid contract.signed from an unrelated producer with an empty payload is rejected, naming every problem', () => {
    const env = validEvent('contract.signed', 'c-1', { producer: 'unrelated-service', payload: {} });
    const errors = validateCanonicalEvent(env, 'contract.signed');
    assert.ok(errors.includes('producer: must be contract-service'));
    for (const field of Object.keys(EVENT_CONTRACTS['contract.signed'].payload)) {
      assert.ok(errors.includes(`payload.${field}: is required`), field);
    }
  });

  it('an unsupported eventVersion is rejected', () => {
    const errors = validateCanonicalEvent(validEvent('contract.settled', 'c-1', { eventVersion: 2 }), 'contract.settled');
    assert.deepEqual(errors, ['eventVersion: supported versions are 1']);
  });

  it('an event delivered to the wrong queue is rejected', () => {
    const errors = validateCanonicalEvent(validEvent('contract.settled', 'c-1'), 'contract.signed');
    assert.deepEqual(errors, ['eventType: this queue only accepts contract.signed']);
  });

  it('a non-object payload is rejected without throwing', () => {
    const env = { ...validEvent('contract.settled', 'c-1'), payload: [] as unknown as Record<string, unknown> };
    assert.deepEqual(validateCanonicalEvent(env, 'contract.settled'), ['payload: must be an object']);
  });

  it('error messages never contain payload values (amounts, ids, e-mail addresses)', () => {
    const payload = { ...validPayload('milestone.settled'), lockedAmount: '12.5', actualAmount: 'SECRET-AMOUNT' };
    const errors = validateCanonicalEvent(validEvent('milestone.settled', 'a', { payload }), 'milestone.settled').join(' ');
    assert.doesNotMatch(errors, /12\.5|SECRET-AMOUNT|buyer@example\.com/);
  });
});
