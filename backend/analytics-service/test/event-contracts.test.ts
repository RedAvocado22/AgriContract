/**
 * Payload validation at the messaging boundary, checked against the frozen contract itself:
 *  - the production rules and golden-flow-events.yaml list the same fields, producers and enum values;
 *  - a small JSON Schema evaluator (the subset the YAML uses) and the production validator must agree on
 *    valid payloads and on hundreds of mutated ones, so a rule that is looser or stricter than the YAML fails.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
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

// ---- minimal JSON Schema evaluator for the subset used by the payload definitions ----
const UUID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
function resolveRef(ref: string): any {
  assert.match(ref, /^#\/\$defs\//, `unsupported $ref ${ref}`);
  return defs[ref.slice('#/$defs/'.length)];
}
function typeOk(type: string, v: unknown): boolean {
  switch (type) {
    case 'string': return typeof v === 'string';
    case 'number': return typeof v === 'number' && Number.isFinite(v);
    case 'integer': return Number.isInteger(v);
    case 'object': return typeof v === 'object' && v !== null && !Array.isArray(v);
    case 'array': return Array.isArray(v);
    case 'null': return v === null;
    case 'boolean': return typeof v === 'boolean';
    default: throw new Error(`unsupported type ${type}`);
  }
}
function formatOk(format: string, v: string): boolean {
  if (format === 'uuid') return UUID.test(v);
  if (format === 'date-time') return /^\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})$/.test(v) && !Number.isNaN(Date.parse(v));
  if (format === 'email') return /^[^\s@]+@[^\s@]+$/.test(v);
  throw new Error(`unsupported format ${format}`);
}
function valid(schema: any, v: unknown): boolean {
  if (schema.$ref) return valid(resolveRef(schema.$ref), v);
  if (schema.allOf && !schema.allOf.every((s: any) => valid(s, v))) return false;
  if (schema.oneOf && schema.oneOf.filter((s: any) => valid(s, v)).length !== 1) return false;
  if ('const' in schema && v !== schema.const) return false;
  if (schema.enum && !schema.enum.includes(v)) return false;
  if (schema.type) {
    const types: string[] = Array.isArray(schema.type) ? schema.type : [schema.type];
    if (!types.some((t) => typeOk(t, v))) return false;
  }
  if (typeof v === 'string') {
    if (schema.pattern && !new RegExp(schema.pattern).test(v)) return false;
    if (schema.maxLength !== undefined && v.length > schema.maxLength) return false;
    if (schema.minLength !== undefined && v.length < schema.minLength) return false;
    if (schema.format && !formatOk(schema.format, v)) return false;
  }
  if (typeof v === 'number') {
    if (schema.exclusiveMinimum !== undefined && !(v > schema.exclusiveMinimum)) return false;
    if (schema.minimum !== undefined && v < schema.minimum) return false;
  }
  if (Array.isArray(v)) {
    if (schema.minItems !== undefined && v.length < schema.minItems) return false;
    if (schema.uniqueItems && new Set(v.map((x) => JSON.stringify(x))).size !== v.length) return false;
    if (schema.items && !v.every((x) => valid(schema.items, x))) return false;
  }
  if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
    const obj = v as Record<string, unknown>;
    for (const r of schema.required ?? []) if (!(r in obj)) return false;
    const props = schema.properties ?? {};
    if (schema.additionalProperties === false && Object.keys(obj).some((k) => !(k in props))) return false;
    for (const [k, s] of Object.entries(props)) if (k in obj && !valid(s, obj[k])) return false;
  }
  return true;
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
    '2026-13-45T00:00:00Z', '2026-10-12', '2026-10-12T05:00:00', 'COFFEE', 'ROBUSTA', 'BUYER', 'VND', [], [null], {}, { userId: 'x' },
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
