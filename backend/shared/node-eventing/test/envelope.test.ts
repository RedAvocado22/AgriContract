import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createEnvelope,
  deriveEnvelope,
  parseEnvelope,
  validateEnvelope,
  type EventEnvelope,
} from '../src/envelope.js';
import { InvalidEnvelopeError } from '../src/errors.js';

const base = {
  eventType: 'analytics.structuring_pattern_detected',
  producer: 'analytics-service',
  aggregateId: 'scan-2026-10-07',
  payload: { userId: 'u-1' },
};

describe('createEnvelope', () => {
  it('fills eventId, eventVersion=1 and a UTC occurredAt, omitting optional fields', () => {
    const env = createEnvelope(base);
    assert.match(env.eventId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.equal(env.eventVersion, 1);
    assert.match(env.occurredAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    assert.deepEqual(Object.keys(env).sort(), [
      'aggregateId',
      'eventId',
      'eventType',
      'eventVersion',
      'occurredAt',
      'payload',
      'producer',
    ]);
  });

  it('keeps correlationId, causationId and subject when given', () => {
    const env = createEnvelope({
      ...base,
      correlationId: 'corr-1',
      causationId: null,
      subject: { type: 'USER_PAIR', id: 'u-1:u-2' },
    });
    assert.equal(env.correlationId, 'corr-1');
    assert.equal(env.causationId, null);
    assert.deepEqual(env.subject, { type: 'USER_PAIR', id: 'u-1:u-2' });
  });

  it('generates a unique eventId per event', () => {
    const ids = new Set(Array.from({ length: 200 }, () => createEnvelope(base).eventId));
    assert.equal(ids.size, 200);
  });

  it('throws InvalidEnvelopeError instead of producing an off-contract event', () => {
    assert.throws(() => createEnvelope({ ...base, eventType: 'StructuringDetected' }), InvalidEnvelopeError);
    assert.throws(() => createEnvelope({ ...base, producer: '' }), InvalidEnvelopeError);
    assert.throws(() => createEnvelope({ ...base, eventVersion: 0 }), InvalidEnvelopeError);
    assert.throws(() => createEnvelope({ ...base, correlationId: '' }), InvalidEnvelopeError);
  });
});

describe('deriveEnvelope', () => {
  it('sets causationId to the cause eventId and carries the correlationId', () => {
    const cause = createEnvelope({ ...base, correlationId: 'saga-42' });
    const derived = deriveEnvelope(cause, { ...base, eventType: 'analytics.projection_updated' });
    assert.equal(derived.causationId, cause.eventId);
    assert.equal(derived.correlationId, 'saga-42');
    assert.notEqual(derived.eventId, cause.eventId);
  });

  it('does not invent a correlationId when the cause had none', () => {
    const cause = createEnvelope(base);
    const derived = deriveEnvelope(cause, base);
    assert.equal('correlationId' in derived, false);
  });
});

describe('validateEnvelope', () => {
  const valid = (): Record<string, unknown> => ({ ...createEnvelope(base) });

  const invalidCases: Array<[string, (e: Record<string, unknown>) => void]> = [
    ['unknown top-level property', (e) => (e.buyerEmail = 'x@y.z')],
    ['missing aggregateId', (e) => delete e.aggregateId],
    ['missing payload', (e) => delete e.payload],
    ['non-uuid eventId', (e) => (e.eventId = '12345')],
    ['eventType without a dot', (e) => (e.eventType = 'signed')],
    ['eventType with upper case', (e) => (e.eventType = 'Contract.Signed')],
    ['string eventVersion', (e) => (e.eventVersion = '1')],
    ['fractional eventVersion', (e) => (e.eventVersion = 1.5)],
    ['occurredAt without time zone', (e) => (e.occurredAt = '2026-10-07T07:00:00')],
    ['occurredAt impossible date', (e) => (e.occurredAt = '2026-02-30T07:00:00Z')],
    ['numeric aggregateId', (e) => (e.aggregateId = 42)],
    ['empty causationId', (e) => (e.causationId = '')],
    ['subject with unknown type', (e) => (e.subject = { type: 'LISTING', id: 'l-1' })],
    ['subject with extra property', (e) => (e.subject = { type: 'SYSTEM', id: 's', note: 'x' })],
    ['subject without id', (e) => (e.subject = { type: 'SYSTEM' })],
    ['array payload', (e) => (e.payload = [])],
    ['null payload', (e) => (e.payload = null)],
  ];

  for (const [name, mutate] of invalidCases) {
    it(`rejects ${name}`, () => {
      const env = valid();
      mutate(env);
      assert.equal(validateEnvelope(env).valid, false);
    });
  }

  it('rejects non-objects', () => {
    assert.equal(validateEnvelope(null).valid, false);
    assert.equal(validateEnvelope([]).valid, false);
    assert.equal(validateEnvelope('{}').valid, false);
  });
});

describe('parseEnvelope (Java producer compatibility)', () => {
  // Shape a Spring/Jackson producer emits: Instant with nanoseconds, explicit nulls.
  const javaJson = JSON.stringify({
    eventId: '3f2c9a1e-8b7d-4c6e-9f10-2a3b4c5d6e7f',
    eventType: 'contract.signed',
    eventVersion: 1,
    occurredAt: '2026-10-07T07:21:15.123456789Z',
    producer: 'contract-service',
    aggregateId: 'c-1001',
    correlationId: 'b0e1c2d3-0000-4000-8000-000000000001',
    causationId: null,
    subject: { type: 'CONTRACT', id: 'c-1001' },
    payload: { contractId: 'c-1001', buyerId: 'u-1', sellerId: 'u-2' },
  });

  it('accepts a Buffer body produced by Java', () => {
    const env: EventEnvelope = parseEnvelope(Buffer.from(javaJson));
    assert.equal(env.eventType, 'contract.signed');
    assert.equal(env.causationId, null);
  });

  it('accepts offsets other than Z', () => {
    const env = JSON.parse(javaJson);
    env.occurredAt = '2026-10-07T14:21:15+07:00';
    assert.equal(validateEnvelope(env).valid, true);
  });

  it('rejects malformed JSON as a permanent error', () => {
    assert.throws(() => parseEnvelope('{not json'), InvalidEnvelopeError);
  });
});
