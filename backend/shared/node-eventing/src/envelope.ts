import { randomUUID } from 'node:crypto';
import { InvalidEnvelopeError } from './errors.js';

/**
 * Node mirror of the frozen contract `contracts/events/event-envelope.yaml`
 * (the same envelope Java producers/consumers use). Field names, required set,
 * nullability and `additionalProperties: false` must stay identical to the YAML;
 * `test/contract.test.ts` fails the build if they drift.
 */

export const SUBJECT_TYPES = ['CONTRACT', 'USER_PAIR', 'SYSTEM'] as const;
export type SubjectType = (typeof SUBJECT_TYPES)[number];

export interface EventSubject {
  type: SubjectType;
  id: string;
}

export type JsonObject = { [key: string]: unknown };

export interface EventEnvelope<P extends JsonObject = JsonObject> {
  eventId: string;
  eventType: string;
  eventVersion: number;
  occurredAt: string;
  producer: string;
  aggregateId: string;
  correlationId?: string | null;
  causationId?: string | null;
  subject?: EventSubject;
  payload: P;
}

/** Required properties, in the order listed by event-envelope.yaml. */
export const ENVELOPE_REQUIRED_FIELDS = [
  'eventId',
  'eventType',
  'eventVersion',
  'occurredAt',
  'producer',
  'aggregateId',
  'payload',
] as const;

/** Every property allowed by event-envelope.yaml (additionalProperties: false). */
export const ENVELOPE_FIELDS = [
  ...ENVELOPE_REQUIRED_FIELDS,
  'correlationId',
  'causationId',
  'subject',
] as const;

export const EVENT_TYPE_PATTERN = /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/;
// RFC 4122 textual form only (36 chars, any version, case-insensitive). Deliberately stricter than
// ajv-formats' `uuid`, which also accepts a "urn:uuid:" prefix: event_id columns are CHAR(36) and the
// Java producers never send the prefix, so a prefixed id could validate here and then fail on insert.
const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
// RFC 3339 date-time; time zone is mandatory ("Z" or +hh:mm). Java Instant serialises as ...Z.
const DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:[Zz]|[+-](\d{2}):(\d{2}))$/;

export interface CreateEnvelopeInput<P extends JsonObject> {
  eventType: string;
  eventVersion?: number;
  producer: string;
  aggregateId: string;
  payload: P;
  /** Mandatory by contract for request/saga/scheduled-workflow events. */
  correlationId?: string | null;
  /** Only for derived events; never fabricate one. Prefer {@link deriveEnvelope}. */
  causationId?: string | null;
  subject?: EventSubject;
  /** Override only for tests or deterministic replays of an existing event. */
  eventId?: string;
  occurredAt?: Date | string;
}

/** Builds a new root (or explicitly caused) envelope and validates it against the contract. */
export function createEnvelope<P extends JsonObject>(input: CreateEnvelopeInput<P>): EventEnvelope<P> {
  const envelope: EventEnvelope<P> = {
    eventId: input.eventId ?? randomUUID(),
    eventType: input.eventType,
    eventVersion: input.eventVersion ?? 1,
    occurredAt: toIsoInstant(input.occurredAt ?? new Date()),
    producer: input.producer,
    aggregateId: input.aggregateId,
    payload: input.payload,
  };
  if (input.correlationId !== undefined) envelope.correlationId = input.correlationId;
  if (input.causationId !== undefined) envelope.causationId = input.causationId;
  if (input.subject !== undefined) envelope.subject = { type: input.subject.type, id: input.subject.id };
  return assertValidEnvelope(envelope) as EventEnvelope<P>;
}

export type DeriveEnvelopeInput<P extends JsonObject> = Omit<CreateEnvelopeInput<P>, 'causationId'>;

/**
 * Builds an event caused by `cause`: causationId = cause.eventId and the workflow
 * correlationId is carried over unless explicitly overridden.
 */
export function deriveEnvelope<P extends JsonObject>(
  cause: Pick<EventEnvelope, 'eventId' | 'correlationId'>,
  input: DeriveEnvelopeInput<P>,
): EventEnvelope<P> {
  const correlationId = input.correlationId !== undefined ? input.correlationId : cause.correlationId;
  return createEnvelope({ ...input, correlationId, causationId: cause.eventId });
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

/** Validates any value against the event-envelope.yaml rules without throwing. */
export function validateEnvelope(value: unknown): ValidationResult {
  const errors: string[] = [];
  if (!isPlainObject(value)) {
    return { valid: false, errors: ['envelope must be a JSON object'] };
  }

  for (const key of Object.keys(value)) {
    if (!(ENVELOPE_FIELDS as readonly string[]).includes(key)) {
      errors.push(`unknown property "${key}" (additionalProperties: false)`);
    }
  }
  for (const key of ENVELOPE_REQUIRED_FIELDS) {
    if (!(key in value)) errors.push(`missing required property "${key}"`);
  }

  const v = value as Record<string, unknown>;
  if ('eventId' in v && !(typeof v.eventId === 'string' && UUID_PATTERN.test(v.eventId))) {
    errors.push('eventId must be a UUID string');
  }
  if ('eventType' in v && !(typeof v.eventType === 'string' && EVENT_TYPE_PATTERN.test(v.eventType))) {
    errors.push(`eventType must match ${EVENT_TYPE_PATTERN.source}`);
  }
  if ('eventVersion' in v && !(Number.isInteger(v.eventVersion) && (v.eventVersion as number) >= 1)) {
    errors.push('eventVersion must be an integer >= 1');
  }
  if ('occurredAt' in v && !(typeof v.occurredAt === 'string' && isRfc3339DateTime(v.occurredAt))) {
    errors.push('occurredAt must be an RFC 3339 date-time with time zone');
  }
  for (const key of ['producer', 'aggregateId'] as const) {
    if (key in v && !isNonEmptyString(v[key])) errors.push(`${key} must be a non-empty string`);
  }
  for (const key of ['correlationId', 'causationId'] as const) {
    if (key in v && v[key] !== null && !isNonEmptyString(v[key])) {
      errors.push(`${key} must be null or a non-empty string`);
    }
  }
  if ('subject' in v) {
    const s = v.subject;
    if (!isPlainObject(s)) {
      errors.push('subject must be an object');
    } else {
      for (const key of Object.keys(s)) {
        if (key !== 'type' && key !== 'id') errors.push(`unknown subject property "${key}"`);
      }
      if (!(SUBJECT_TYPES as readonly unknown[]).includes(s.type)) {
        errors.push(`subject.type must be one of ${SUBJECT_TYPES.join(', ')}`);
      }
      if (!isNonEmptyString(s.id)) errors.push('subject.id must be a non-empty string');
    }
  }
  if ('payload' in v && !isPlainObject(v.payload)) {
    errors.push('payload must be a JSON object');
  }

  return { valid: errors.length === 0, errors };
}

export function assertValidEnvelope(value: unknown): EventEnvelope {
  const result = validateEnvelope(value);
  if (!result.valid) throw new InvalidEnvelopeError(result.errors);
  return value as EventEnvelope;
}

/** Parses a broker message body (Buffer/string/object) into a validated envelope. */
export function parseEnvelope(body: Buffer | string | unknown): EventEnvelope {
  let value: unknown = body;
  if (Buffer.isBuffer(body)) value = body.toString('utf8');
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      throw new InvalidEnvelopeError(['message body is not valid JSON']);
    }
  }
  return assertValidEnvelope(value);
}

function toIsoInstant(value: Date | string): string {
  if (typeof value === 'string') return value;
  if (Number.isNaN(value.getTime())) throw new InvalidEnvelopeError(['occurredAt is an invalid Date']);
  return value.toISOString();
}

function isRfc3339DateTime(value: string): boolean {
  const m = DATE_TIME_PATTERN.exec(value);
  if (!m) return false;
  const [, y, mo, d, h, mi, s, tzh, tzm] = m;
  const year = Number(y);
  const month = Number(mo);
  const day = Number(d);
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth) return false;
  if (Number(h) > 23 || Number(mi) > 59 || Number(s) > 60) return false;
  if (tzh !== undefined && (Number(tzh) > 23 || Number(tzm) > 59)) return false;
  return true;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
