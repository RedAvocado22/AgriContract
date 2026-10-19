import { NonRetryableError, type EventEnvelope } from '@agricontract/eventing';
import type { IngestedEventType } from '../../domain/ingestedEvents.js';

/**
 * Producer, version and payload rules for every event analytics ingests, mirrored from the frozen
 * contract contracts/events/golden-flow-events.yaml (the envelope itself is validated by
 * @agricontract/eventing). test/event-contracts.test.ts compares this table with the YAML, field by
 * field, and runs the same valid/invalid payloads through both, so drift fails the build.
 *
 * Validation errors name the field and the rule, never the value: payloads carry money amounts and ids.
 */

export type Rule =
  | { kind: 'uuid' }
  | { kind: 'money'; maxLength?: number }
  | { kind: 'positiveNumber' }
  | { kind: 'dateTime' }
  | { kind: 'const'; value: string }
  | { kind: 'enum'; values: readonly string[] }
  | { kind: 'sha256' }
  | { kind: 'uuidSet' }
  | { kind: 'recipients' }
  | { kind: 'nullable'; rule: Rule };

export interface EventContract {
  producer: string;
  /** Envelope eventVersion values this build understands; others go to the DLQ for a developer. */
  eventVersions: readonly number[];
  /** Every payload field is required and no other field is allowed (additionalProperties: false). */
  payload: Readonly<Record<string, Rule>>;
}

const uuid: Rule = { kind: 'uuid' };
const money: Rule = { kind: 'money' };
const dateTime: Rule = { kind: 'dateTime' };
const nullable = (rule: Rule): Rule => ({ kind: 'nullable', rule });
const oneOf = (...values: string[]): Rule => ({ kind: 'enum', values });

const BREACH_REASON_CODES = [
  'NON_DELIVERY', 'SHORTFALL', 'LATE_DELIVERY', 'QUALITY_BELOW_COMMITTED', 'SIDE_SELLING',
  'PRODUCTION_SHOCK_NON_FM', 'FUNDING_FAILURE', 'LATE_PAYMENT', 'FAILURE_TO_RECEIVE',
  'WRONGFUL_REJECTION', 'FORCE_MAJEURE', 'MUTUAL_EXIT', 'ACTIVATION_FAILURE',
] as const;

const CONTRACT_SERVICE = 'contract-service';

export const EVENT_CONTRACTS: Readonly<Record<IngestedEventType, EventContract>> = {
  'contract.signed': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      listingId: uuid,
      reservedQuantity: { kind: 'positiveNumber' },
      commodity: { kind: 'const', value: 'COFFEE' },
      buyerId: uuid,
      sellerId: uuid,
      totalAmount: money,
      buyerDepositAmount: money,
      sellerDepositAmount: money,
      signedContentHash: { kind: 'sha256' },
      signedAt: dateTime,
    },
  },
  'milestone.settled': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      milestoneId: uuid,
      lockedAmount: money,
      actualAmount: money,
      recipients: { kind: 'recipients' },
    },
  },
  'milestone.cancelled_with_penalty': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      milestoneId: uuid,
      remedyDecisionId: uuid,
      finalBreachingRole: oneOf('BUYER', 'SELLER'),
      breachReasonCode: { kind: 'enum', values: BREACH_REASON_CODES },
      penaltyAmount: money,
    },
  },
  'milestone.force_majeure_resolved': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      milestoneId: uuid,
      forceMajeureClaimId: uuid,
      decision: oneOf('APPROVED', 'REJECTED'),
    },
  },
  'milestone.price_adjusted': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      milestoneId: uuid,
      baseUnitPrice: { kind: 'money', maxLength: 11 },
      effectiveUnitPrice: { kind: 'money', maxLength: 11 },
      currency: { kind: 'const', value: 'VND' },
      proposerId: uuid,
      accepterId: uuid,
      acceptedAt: dateTime,
    },
  },
  'contract.settled': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      buyerId: uuid,
      sellerId: uuid,
    },
  },
  'contract.terminated': {
    producer: CONTRACT_SERVICE,
    eventVersions: [1],
    payload: {
      contractId: uuid,
      terminationType: oneOf(
        'WITHDRAW_OFFER', 'MUTUAL_TERMINATION', 'MUTUAL_REPLACEMENT', 'TERMINATION_FOR_BREACH',
        'TERMINATION_FOR_FORCE_MAJEURE', 'ACTIVATION_FAILURE',
      ),
      requestedBy: oneOf('BUYER', 'SELLER', 'SYSTEM'),
      finalBreachingRole: nullable(oneOf('BUYER', 'SELLER')),
      breachReasonCode: nullable({ kind: 'enum', values: BREACH_REASON_CODES }),
      remedyDecisionId: nullable(uuid),
      breachCaseId: nullable(uuid),
      affectedMilestoneIds: { kind: 'uuidSet' },
      supersededByContractId: nullable(uuid),
      replacesContractId: nullable(uuid),
    },
  },
};

// RFC 4122 textual form only, like the envelope validation in @agricontract/eventing.
const UUID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const MONEY = /^[0-9]+\.[0-9]{2}$/;
const SHA256 = /^[a-f0-9]{64}$/;
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIME = /^(\d{2}):(\d{2}):(\d{2}(?:\.\d+)?)(z|([+-])(\d{2})(?::?(\d{2}))?)$/i;
const DAYS_IN_MONTH = [0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
const EMAIL = /^[^\s@]+@[^\s@]+$/;

const hasOwn = (o: object, key: string): boolean => Object.prototype.hasOwnProperty.call(o, key);

const isLeapYear = (year: number): boolean => year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);

function isCalendarDate(text: string): boolean {
  const m = DATE.exec(text);
  if (!m) return false;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  if (month < 1 || month > 12 || day < 1) return false;
  return day <= (month === 2 && isLeapYear(year) ? 29 : DAYS_IN_MONTH[month]!);
}

/** Time of day with a mandatory zone. A leap second (:60) is only valid at 23:59:60 UTC. */
function isTimeWithZone(text: string): boolean {
  const m = TIME.exec(text);
  if (!m) return false;
  const hour = Number(m[1]);
  const minute = Number(m[2]);
  const second = Number(m[3]);
  const sign = m[5] === '-' ? -1 : 1;
  const zoneHour = Number(m[6] ?? 0);
  const zoneMinute = Number(m[7] ?? 0);
  if (zoneHour > 23 || zoneMinute > 59) return false;
  if (hour <= 23 && minute <= 59 && second < 60) return true;
  const utcMinute = minute - zoneMinute * sign;
  const utcHour = hour - zoneHour * sign - (utcMinute < 0 ? 1 : 0);
  return (utcHour === 23 || utcHour === -1) && (utcMinute === 59 || utcMinute === -1) && second < 61;
}

/**
 * RFC 3339 date-time, checked field by field (same rules as the JSON Schema "date-time" format of the
 * frozen contract). Date.parse is not used: it rolls impossible dates over (2026-02-30 becomes 2 March).
 */
export function isDateTime(value: string): boolean {
  const parts = value.split(/t|\s/i);
  return parts.length === 2 && isCalendarDate(parts[0]!) && isTimeWithZone(parts[1]!);
}

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

/** Returns a reason (without the value) when `value` breaks `rule`, or null when it is fine. */
export function checkRule(rule: Rule, value: unknown): string | null {
  switch (rule.kind) {
    case 'uuid':
      return typeof value === 'string' && UUID.test(value) ? null : 'must be a UUID';
    case 'money':
      return typeof value === 'string' && MONEY.test(value) && value.length <= (rule.maxLength ?? 19)
        ? null
        : `must be a VND amount with exactly 2 decimals (at most ${rule.maxLength ?? 19} characters)`;
    case 'positiveNumber':
      return typeof value === 'number' && Number.isFinite(value) && value > 0 ? null : 'must be a number greater than 0';
    case 'dateTime':
      return typeof value === 'string' && isDateTime(value)
        ? null
        : 'must be an RFC 3339 date-time with a time zone';
    case 'const':
      return value === rule.value ? null : `must be ${rule.value}`;
    case 'enum':
      return typeof value === 'string' && rule.values.includes(value) ? null : `must be one of ${rule.values.join(', ')}`;
    case 'sha256':
      return typeof value === 'string' && SHA256.test(value) ? null : 'must be a lowercase hex SHA-256';
    case 'uuidSet':
      return Array.isArray(value) &&
        value.every((v) => typeof v === 'string' && UUID.test(v)) &&
        new Set(value).size === value.length
        ? null
        : 'must be an array of unique UUIDs';
    case 'recipients':
      return Array.isArray(value) && value.every(isRecipient) ? null : 'must be an array of {userId, email, role}';
    case 'nullable':
      return value === null ? null : checkRule(rule.rule, value);
  }
}

function isRecipient(v: unknown): boolean {
  if (!isObject(v)) return false;
  const keys = Object.keys(v);
  return (
    keys.length === 3 &&
    checkRule(uuid, v.userId) === null &&
    typeof v.email === 'string' &&
    EMAIL.test(v.email) &&
    (v.role === 'BUYER' || v.role === 'SELLER')
  );
}

/**
 * Validates producer, version and payload of an already envelope-valid event that arrived on the queue
 * of `expectedType`. Returns one message per problem; an empty list means the event is acceptable.
 */
export function validateCanonicalEvent(envelope: EventEnvelope, expectedType: string): string[] {
  const contract = (EVENT_CONTRACTS as Record<string, EventContract | undefined>)[envelope.eventType];
  if (envelope.eventType !== expectedType) return [`eventType: this queue only accepts ${expectedType}`];
  if (!contract) return [`eventType: ${envelope.eventType} is not an ingested canonical event`];

  const errors: string[] = [];
  if (envelope.producer !== contract.producer) errors.push(`producer: must be ${contract.producer}`);
  if (!contract.eventVersions.includes(envelope.eventVersion)) {
    errors.push(`eventVersion: supported versions are ${contract.eventVersions.join(', ')}`);
  }

  const payload: unknown = envelope.payload;
  if (!isObject(payload)) return [...errors, 'payload: must be an object'];
  for (const [field, rule] of Object.entries(contract.payload)) {
    if (!hasOwn(payload, field)) errors.push(`payload.${field}: is required`);
    else {
      const problem = checkRule(rule, payload[field]);
      if (problem) errors.push(`payload.${field}: ${problem}`);
    }
  }
  for (const field of Object.keys(payload)) {
    if (!hasOwn(contract.payload, field)) errors.push(`payload.${field}: is not allowed`);
  }
  return errors;
}

/** Permanent failure: the event can never be ingested as-is, so it goes straight to the DLQ (no retry). */
export class InvalidEventError extends NonRetryableError {
  readonly errors: string[];

  constructor(eventType: string, errors: string[]) {
    super(`Invalid ${eventType} event: ${errors.join('; ')}`);
    this.name = 'InvalidEventError';
    this.errors = errors;
  }
}
