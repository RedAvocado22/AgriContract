/**
 * Boundary contract test: the Node envelope must match the frozen
 * contracts/events/event-envelope.yaml that the Java services implement.
 * The YAML is the single source of truth; this test fails on any drift.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import Ajv2020Module from 'ajv/dist/2020.js';
import addFormatsModule from 'ajv-formats';
import yaml from 'js-yaml';
import {
  ENVELOPE_FIELDS,
  ENVELOPE_REQUIRED_FIELDS,
  SUBJECT_TYPES,
  createEnvelope,
  deriveEnvelope,
  validateEnvelope,
} from '../src/envelope.js';

const here = dirname(fileURLToPath(import.meta.url));
const CONTRACT_PATH = resolve(here, '../../../../contracts/events/event-envelope.yaml');

type Schema = {
  required: string[];
  properties: Record<string, { pattern?: string }>;
  additionalProperties: boolean;
  $defs: { subject: { properties: { type: { enum: string[] } } } };
};

const schema = yaml.load(readFileSync(CONTRACT_PATH, 'utf8')) as Schema;

// CJS/ESM interop for ajv packages.
const Ajv2020 = ((Ajv2020Module as unknown as { default?: unknown }).default ?? Ajv2020Module) as new (
  opts: object,
) => { compile(s: object): ((d: unknown) => boolean) & { errors?: unknown } };
const addFormats = ((addFormatsModule as unknown as { default?: unknown }).default ?? addFormatsModule) as (
  ajv: unknown,
) => void;

const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const contractValidate = ajv.compile(schema as object);

function sortStrings(values: readonly string[]): string[] {
  return [...values].sort();
}

describe('event-envelope.yaml contract', () => {
  it('Node allows exactly the properties the contract allows', () => {
    assert.equal(schema.additionalProperties, false);
    assert.deepEqual(sortStrings(ENVELOPE_FIELDS), sortStrings(Object.keys(schema.properties)));
  });

  it('Node requires exactly the properties the contract requires', () => {
    assert.deepEqual(sortStrings(ENVELOPE_REQUIRED_FIELDS), sortStrings(schema.required));
  });

  it('subject types match the contract enum', () => {
    assert.deepEqual(sortStrings(SUBJECT_TYPES), sortStrings(schema.$defs.subject.properties.type.enum));
  });

  it('every envelope the Node builders produce validates against the contract', () => {
    const root = createEnvelope({
      eventType: 'pricing.reference_price_ingested',
      producer: 'pricing-service',
      aggregateId: 'COFFEE:DAK_LAK',
      payload: { commodity: 'COFFEE', price: '120000.00' },
    });
    const full = createEnvelope({
      eventType: 'analytics.structuring_pattern_detected',
      eventVersion: 2,
      producer: 'analytics-service',
      aggregateId: 'scan-1',
      correlationId: 'job-2026-10-07',
      causationId: null,
      subject: { type: 'USER_PAIR', id: 'u-1:u-2' },
      payload: {},
    });
    const derived = deriveEnvelope(full, {
      eventType: 'file.ready',
      producer: 'file-service',
      aggregateId: 'f-1',
      payload: { fileId: 'f-1' },
    });
    for (const env of [root, full, derived]) {
      const ok = contractValidate(env);
      assert.equal(ok, true, JSON.stringify(contractValidate.errors));
    }
  });

  // Node validator and the contract must agree on every fixture (no looser, no stricter).
  const good = () => ({
    eventId: '3f2c9a1e-8b7d-4c6e-9f10-2a3b4c5d6e7f',
    eventType: 'contract.signed',
    eventVersion: 1,
    occurredAt: '2026-10-07T07:21:15.123456789Z',
    producer: 'contract-service',
    aggregateId: 'c-1001',
    payload: {},
  });
  const fixtures: Array<[string, Record<string, unknown>]> = [
    ['minimal Java envelope', good()],
    ['explicit nulls', { ...good(), correlationId: null, causationId: null }],
    ['with subject', { ...good(), subject: { type: 'SYSTEM', id: 'sys' } }],
    ['offset time zone', { ...good(), occurredAt: '2026-10-07T14:21:15+07:00' }],
    ['unknown property', { ...good(), buyerEmail: 'a@b.c' }],
    ['missing producer', (({ producer: _p, ...rest }) => rest)(good())],
    ['bad eventType', { ...good(), eventType: 'contract' }],
    ['eventVersion 0', { ...good(), eventVersion: 0 }],
    ['non-integer eventVersion', { ...good(), eventVersion: 1.5 }],
    ['bad uuid', { ...good(), eventId: 'not-a-uuid' }],
    ['local time without zone', { ...good(), occurredAt: '2026-10-07T07:21:15' }],
    ['empty correlationId', { ...good(), correlationId: '' }],
    ['bad subject type', { ...good(), subject: { type: 'LISTING', id: 'x' } }],
    ['subject extra key', { ...good(), subject: { type: 'SYSTEM', id: 'x', extra: 1 } }],
    ['empty aggregateId', { ...good(), aggregateId: '' }],
    ['array payload', { ...good(), payload: [] }],
  ];

  for (const [name, fixture] of fixtures) {
    it(`agrees with the contract on: ${name}`, () => {
      const contractSays = contractValidate(fixture) as boolean;
      const nodeSays = validateEnvelope(fixture).valid;
      assert.equal(nodeSays, contractSays, `contract=${contractSays} node=${nodeSays}`);
    });
  }
});
