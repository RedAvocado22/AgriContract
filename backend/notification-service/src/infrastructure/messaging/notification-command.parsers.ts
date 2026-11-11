import {
  InvalidEnvelopeError,
  NonRetryableError,
  parseEnvelope,
  type EventEnvelope,
} from '@agricontract/eventing';
import {
  ACTIVATION_NEXT_ACTIONS,
  BREACH_REASON_CODES,
  CANCEL_REQUESTER_ROLES,
  FAILED_LEGS,
  FUND_TYPES,
  KYC_RESULTS,
  LOCK_ACTIONS,
  PARTY_ROLES,
  RECIPIENT_ROLES,
  REMEDY_TYPES,
  TERMINATION_REQUESTERS,
  TERMINATION_TYPES,
  type ActivationCancelRequestedCommand,
  type ContractActivationFailedRequestedCommand,
  type ContractTerminatedRequestedCommand,
  type RemedyLeg,
  type ContractAnchorRequestedCommand,
  type NotificationRecipient,
  type RecipientRole,
  type UserKycResultRequestedCommand,
  type UserLockChangedRequestedCommand,
} from '../../application/commands';

/*
 * Parsers for the Phase 2 notification commands of
 * contracts/events/notification-commands.yaml. Unlike the flat Phase 1 events
 * (legacy-payload.parsers.ts), a command is a full event envelope and its
 * recipients live in `payload.recipients`, so parseEnvelope runs first and the
 * payload is read after it.
 *
 * Every failure is a NonRetryableError: the message goes to the DLQ at once
 * instead of being retried three times, because no redelivery of the same
 * bytes can become valid. parseEnvelope's own InvalidEnvelopeError is
 * converted, since the queue consumer only passes a NonRetryableError through
 * unchanged (anything else it replaces with a retryable code).
 *
 * What the messages may say: a field name, a JSON type, the index of a bad
 * recipient and the event id when it is a plain id. Never a value, because
 * these messages are logged on every dead-letter and `payload.recipients` holds
 * email addresses (review #172).
 *
 * The envelope checks are the command contract's, which is stricter than
 * event-envelope.yaml on two points: `correlationId` is required, and
 * `subject` is not among the allowed properties.
 */

/** UUIDs and ids like evt-anchor-1729: safe to log, unlike free text or emails. */
const SAFE_ID = /^[A-Za-z0-9-]{1,64}$/;

const UUID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

const SHA256_HEX = /^[a-f0-9]{64}$/;

/** The contract's `money`: non-negative VND with two decimals, at most 19 characters. */
const MONEY = /^[0-9]+\.[0-9]{2}$/;
const MONEY_MAX_LENGTH = 19;

const REMEDY_LEG_FIELDS = [
  'remedyLegId',
  'remedyType',
  'fundType',
  'role',
  'amount',
] as const;

/** RFC 3339, the shape every producer's Instant serialises to. */
const DATE_TIME =
  /^\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[Zz]|[+-]\d{2}:\d{2})$/;

/**
 * An address the contract's `format: email` can accept. Deliberately not
 * stricter than that format: a recipient the publisher considers valid must
 * reach SMTP, not the DLQ. The value is never quoted in the error.
 */
const EMAIL = /^[^\s@]+@[^\s@]+$/;

/** Properties the command envelope allows; `subject` of event-envelope.yaml is not one. */
const ENVELOPE_PROPERTIES = [
  'eventId',
  'eventType',
  'eventVersion',
  'occurredAt',
  'producer',
  'aggregateId',
  'correlationId',
  'causationId',
  'payload',
] as const;

class CommandReader {
  constructor(
    private readonly eventType: string,
    private readonly eventId: string,
    private readonly payload: Record<string, unknown>,
  ) {}

  /** Rejects any payload property the command does not declare (additionalProperties: false). */
  only(fields: readonly string[]): this {
    for (const key of Object.keys(this.payload)) {
      if (!fields.includes(key)) {
        throw this.error(`unknown payload property "${key}"`);
      }
    }
    return this;
  }

  uuid(field: string): string {
    const value = this.string(field);
    if (!UUID.test(value)) throw this.error(`field "${field}" must be a UUID`);
    return value;
  }

  hash(field: string): string {
    const value = this.string(field);
    if (!SHA256_HEX.test(value)) {
      throw this.error(`field "${field}" must be 64 lowercase hex characters`);
    }
    return value;
  }

  dateTime(field: string): string {
    const value = this.string(field);
    if (!DATE_TIME.test(value)) {
      throw this.error(`field "${field}" must be an RFC 3339 date-time`);
    }
    return value;
  }

  /** One of the contract's enum values; the bad value is not quoted. */
  enumValue<T extends string>(field: string, values: readonly T[]): T {
    const value = this.payload[field];
    if (!(values as readonly unknown[]).includes(value)) {
      throw this.error(`field "${field}" must be one of ${values.join(', ')}`);
    }
    return value as T;
  }

  /**
   * Any JSON string, the empty one included, as a bare `{type: string}` is
   * declared: rejecting more would dead-letter a command the contract allows.
   */
  text(field: string): string {
    const value = this.payload[field];
    if (typeof value !== 'string') {
      throw this.error(
        `field "${field}" must be a string, got ${describe(value)}`,
      );
    }
    return value;
  }

  /**
   * An optional bare `{type: string}`: absent is null, but a property that is
   * present must be a string, since the contract does not allow null.
   */
  optionalText(field: string): string | null {
    return field in this.payload ? this.text(field) : null;
  }

  /**
   * An optional date-time that may also be null, as `{type: [string, "null"]}`
   * outside `required` is declared: absent and null both read as null.
   */
  optionalNullableDateTime(field: string): string | null {
    const value = this.payload[field];
    return value === undefined || value === null ? null : this.dateTime(field);
  }

  /** A single address outside `payload.recipients`, checked as a recipient's is; never quoted. */
  email(field: string): string {
    const value = this.payload[field];
    if (typeof value !== 'string' || !EMAIL.test(value)) {
      throw this.error(`field "${field}" must be an email address`);
    }
    return value;
  }

  /**
   * An array of the contract's enum values, possibly empty; duplicates are
   * allowed because the contract does not declare uniqueItems. The bad value
   * is not quoted, only its index.
   */
  enumArray<T extends string>(field: string, values: readonly T[]): T[] {
    const value = this.payload[field];
    if (!Array.isArray(value)) {
      throw this.error(
        `field "${field}" must be an array, got ${describe(value)}`,
      );
    }
    value.forEach((item, i) => {
      if (!(values as readonly unknown[]).includes(item)) {
        throw this.error(`${field}[${i}] must be one of ${values.join(', ')}`);
      }
    });
    return (value as unknown[]).slice() as T[];
  }

  /** A UUID or null; the property itself is required (absent is not null). */
  nullableUuid(field: string): string | null {
    this.present(field);
    return this.payload[field] === null ? null : this.uuid(field);
  }

  /** One of the enum values or null; the property itself is required. */
  nullableEnum<T extends string>(
    field: string,
    values: readonly T[],
  ): T | null {
    this.present(field);
    return this.payload[field] === null ? null : this.enumValue(field, values);
  }

  /** An array of distinct UUIDs (uniqueItems), possibly empty; bad items by index only. */
  uuidSet(field: string): string[] {
    const value = this.payload[field];
    if (!Array.isArray(value)) {
      throw this.error(
        `field "${field}" must be an array, got ${describe(value)}`,
      );
    }
    value.forEach((item, i) => {
      if (typeof item !== 'string' || !UUID.test(item)) {
        throw this.error(`${field}[${i}] must be a UUID`);
      }
      if (value.indexOf(item) !== i) {
        throw this.error(`${field}[${i}] repeats an earlier item`);
      }
    });
    return (value as string[]).slice();
  }

  /** `remedyLegs`: possibly empty, each exactly the contract's `remedyLeg`. */
  remedyLegs(): RemedyLeg[] {
    const value = this.payload.remedyLegs;
    if (!Array.isArray(value)) {
      throw this.error(
        `field "remedyLegs" must be an array, got ${describe(value)}`,
      );
    }
    return value.map((item, i) => this.remedyLeg(item, i));
  }

  private remedyLeg(value: unknown, index: number): RemedyLeg {
    const at = `remedyLegs[${index}]`;
    if (!isPlainObject(value)) {
      throw this.error(`${at} must be a JSON object, got ${describe(value)}`);
    }
    for (const key of Object.keys(value)) {
      if (!(REMEDY_LEG_FIELDS as readonly string[]).includes(key)) {
        throw this.error(`unknown property "${key}" in ${at}`);
      }
    }
    const oneOf = <T extends string>(
      field: string,
      values: readonly T[],
    ): T => {
      if (!(values as readonly unknown[]).includes(value[field])) {
        throw this.error(`${at}.${field} must be one of ${values.join(', ')}`);
      }
      return value[field] as T;
    };

    const id = value.remedyLegId;
    if (typeof id !== 'string' || !UUID.test(id)) {
      throw this.error(`${at}.remedyLegId must be a UUID`);
    }
    const amount = value.amount;
    if (
      typeof amount !== 'string' ||
      amount.length > MONEY_MAX_LENGTH ||
      !MONEY.test(amount)
    ) {
      throw this.error(
        `${at}.amount must be a decimal string with two decimals, at most ${MONEY_MAX_LENGTH} characters`,
      );
    }
    return {
      remedyLegId: id,
      remedyType: oneOf('remedyType', REMEDY_TYPES),
      fundType: oneOf('fundType', FUND_TYPES),
      role: oneOf('role', PARTY_ROLES),
      amount,
    };
  }

  /** A required property that may hold null. */
  private present(field: string): void {
    if (!(field in this.payload))
      throw this.error(`field "${field}" is missing`);
  }

  /** A JSON object of any shape, as `contractTermsSnapshot` is declared. */
  object(field: string): Record<string, unknown> {
    const value = this.payload[field];
    if (!isPlainObject(value)) {
      throw this.error(
        `field "${field}" must be a JSON object, got ${describe(value)}`,
      );
    }
    return value;
  }

  /** `payload.recipients`: at least one, each exactly {userId, email, role}. */
  recipients(): NotificationRecipient[] {
    const value = this.payload.recipients;
    if (!Array.isArray(value) || value.length === 0) {
      throw this.error(
        `field "recipients" must be a non-empty array, got ${describe(value)}`,
      );
    }
    return value.map((item, i) => this.recipient(item, i));
  }

  private recipient(value: unknown, index: number): NotificationRecipient {
    const at = `recipients[${index}]`;
    if (!isPlainObject(value)) {
      throw this.error(`${at} must be a JSON object, got ${describe(value)}`);
    }
    for (const key of Object.keys(value)) {
      if (!['userId', 'email', 'role'].includes(key)) {
        throw this.error(`unknown property "${key}" in ${at}`);
      }
    }

    const userId = value.userId;
    // Nullable by contract, but absent is not null: the property is required.
    if (!('userId' in value)) throw this.error(`${at}.userId is missing`);
    if (userId !== null && !(typeof userId === 'string' && UUID.test(userId))) {
      throw this.error(`${at}.userId must be a UUID or null`);
    }

    const email = value.email;
    if (typeof email !== 'string' || !EMAIL.test(email)) {
      throw this.error(`${at}.email must be an email address`);
    }

    const role = value.role;
    if (
      typeof role !== 'string' ||
      !(RECIPIENT_ROLES as readonly string[]).includes(role)
    ) {
      throw this.error(
        `${at}.role must be one of ${RECIPIENT_ROLES.join(', ')}`,
      );
    }

    return { userId, email, role: role as RecipientRole };
  }

  private string(field: string): string {
    const value = this.payload[field];
    if (typeof value !== 'string' || value === '') {
      throw this.error(
        `field "${field}" must be a non-empty string, got ${describe(value)}`,
      );
    }
    return value;
  }

  error(reason: string): NonRetryableError {
    return malformed(this.eventType, this.eventId, reason);
  }
}

/** The one error shape of this module: never quotes a payload value. */
function malformed(
  eventType: string,
  eventId: string,
  reason: string,
): NonRetryableError {
  const event = SAFE_ID.test(eventId) ? ` (event ${eventId})` : '';
  return new NonRetryableError(
    `Malformed ${eventType} command: ${reason}${event}`,
  );
}

/**
 * A well-formed command this service cannot mail yet (a termination type
 * without a template): dead-lettered at once, like a malformed one, so it
 * waits in the DLQ for the template instead of being acked unsent. `reason`
 * must quote enum values only.
 */
export function unsupportedCommand(
  eventType: string,
  eventId: string,
  reason: string,
): NonRetryableError {
  const event = SAFE_ID.test(eventId) ? ` (event ${eventId})` : '';
  return new NonRetryableError(
    `Unsupported ${eventType} command: ${reason}${event}`,
  );
}

/**
 * Validates the envelope of a command and returns a reader over its payload.
 * `producer` is checked against the contract's `const`: a command from another
 * service is not the one this template renders.
 */
function readCommand(
  body: Buffer,
  eventType: string,
  producer: string,
): { envelope: EventEnvelope; payload: CommandReader } {
  let envelope: EventEnvelope;
  try {
    envelope = parseEnvelope(body);
  } catch (err) {
    if (err instanceof InvalidEnvelopeError) {
      throw new NonRetryableError(
        `Malformed ${eventType} command: ${err.message}`,
      );
    }
    throw err;
  }

  const fail = (reason: string) =>
    malformed(eventType, envelope.eventId, reason);

  for (const key of Object.keys(envelope as unknown as object)) {
    if (!(ENVELOPE_PROPERTIES as readonly string[]).includes(key)) {
      throw fail(`unknown envelope property "${key}"`);
    }
  }
  // eventType is pattern-checked by the envelope, so quoting it leaks nothing.
  if (envelope.eventType !== eventType) {
    throw fail(`eventType "${envelope.eventType}" is not this queue's command`);
  }
  if (envelope.producer !== producer) {
    throw fail(`producer must be "${producer}"`);
  }
  if (
    typeof envelope.correlationId !== 'string' ||
    !UUID.test(envelope.correlationId)
  ) {
    throw fail('correlationId must be a UUID');
  }
  if (!isPlainObject(envelope.payload)) {
    throw fail('payload must be a JSON object');
  }

  return {
    envelope,
    payload: new CommandReader(eventType, envelope.eventId, envelope.payload),
  };
}

export const CONTRACT_ANCHOR_REQUESTED =
  'notification.contract_anchor_requested';

/** Payload properties of `contractAnchorRequested`, all required. */
export const CONTRACT_ANCHOR_FIELDS = [
  'contractId',
  'recipients',
  'contractTermsSnapshot',
  'signedContentHash',
  'signedAt',
] as const;

export function parseContractAnchorRequested(
  body: Buffer,
): ContractAnchorRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    CONTRACT_ANCHOR_REQUESTED,
    'contract-service',
  );
  payload.only(CONTRACT_ANCHOR_FIELDS);

  return {
    eventId: envelope.eventId,
    contractId: payload.uuid('contractId'),
    recipients: payload.recipients(),
    contractTermsSnapshot: payload.object('contractTermsSnapshot'),
    signedContentHash: payload.hash('signedContentHash'),
    signedAt: payload.dateTime('signedAt'),
  };
}

export const ACTIVATION_CANCEL_REQUESTED =
  'notification.activation_cancel_requested';

/** Payload properties of `activationCancelRequested`, all required. */
export const ACTIVATION_CANCEL_FIELDS = [
  'contractId',
  'recipients',
  'requestedByRole',
  'oneSidedCancelAllowedFrom',
] as const;

export function parseActivationCancelRequested(
  body: Buffer,
): ActivationCancelRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    ACTIVATION_CANCEL_REQUESTED,
    'contract-service',
  );
  payload.only(ACTIVATION_CANCEL_FIELDS);

  return {
    eventId: envelope.eventId,
    contractId: payload.uuid('contractId'),
    recipients: payload.recipients(),
    requestedByRole: payload.enumValue(
      'requestedByRole',
      CANCEL_REQUESTER_ROLES,
    ),
    oneSidedCancelAllowedFrom: payload.dateTime('oneSidedCancelAllowedFrom'),
  };
}

export const CONTRACT_ACTIVATION_FAILED_REQUESTED =
  'notification.contract_activation_failed_requested';

/** Payload properties of `contractActivationFailedRequested`, all required. */
export const CONTRACT_ACTIVATION_FAILED_FIELDS = [
  'contractId',
  'recipients',
  'failedLeg',
  'buyerDepositState',
  'sellerDepositState',
  'nextActions',
  'oneSidedCancelAllowedFrom',
] as const;

export function parseContractActivationFailedRequested(
  body: Buffer,
): ContractActivationFailedRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    CONTRACT_ACTIVATION_FAILED_REQUESTED,
    'contract-service',
  );
  payload.only(CONTRACT_ACTIVATION_FAILED_FIELDS);

  return {
    eventId: envelope.eventId,
    contractId: payload.uuid('contractId'),
    recipients: payload.recipients(),
    failedLeg: payload.enumValue('failedLeg', FAILED_LEGS),
    buyerDepositState: payload.text('buyerDepositState'),
    sellerDepositState: payload.text('sellerDepositState'),
    nextActions: payload.enumArray('nextActions', ACTIVATION_NEXT_ACTIONS),
    oneSidedCancelAllowedFrom: payload.dateTime('oneSidedCancelAllowedFrom'),
  };
}

export const CONTRACT_TERMINATED_REQUESTED =
  'notification.contract_terminated_requested';

/** Payload properties of `contractTerminatedRequested`, all required. */
export const CONTRACT_TERMINATED_FIELDS = [
  'contractId',
  'recipients',
  'terminationType',
  'requestedBy',
  'finalBreachingRole',
  'breachReasonCode',
  'remedyDecisionId',
  'breachCaseId',
  'affectedMilestoneIds',
  'supersededByContractId',
  'replacesContractId',
  'remedyLegs',
] as const;

/**
 * Every termination type parses, so the whole contract is checked here; which
 * types have an email is decided after parsing (command-routes.ts).
 */
export function parseContractTerminatedRequested(
  body: Buffer,
): ContractTerminatedRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    CONTRACT_TERMINATED_REQUESTED,
    'contract-service',
  );
  payload.only(CONTRACT_TERMINATED_FIELDS);

  return {
    eventId: envelope.eventId,
    contractId: payload.uuid('contractId'),
    recipients: payload.recipients(),
    terminationType: payload.enumValue('terminationType', TERMINATION_TYPES),
    requestedBy: payload.enumValue('requestedBy', TERMINATION_REQUESTERS),
    finalBreachingRole: payload.nullableEnum('finalBreachingRole', PARTY_ROLES),
    breachReasonCode: payload.nullableEnum(
      'breachReasonCode',
      BREACH_REASON_CODES,
    ),
    remedyDecisionId: payload.nullableUuid('remedyDecisionId'),
    breachCaseId: payload.nullableUuid('breachCaseId'),
    affectedMilestoneIds: payload.uuidSet('affectedMilestoneIds'),
    supersededByContractId: payload.nullableUuid('supersededByContractId'),
    replacesContractId: payload.nullableUuid('replacesContractId'),
    remedyLegs: payload.remedyLegs(),
  };
}

export const USER_KYC_RESULT_REQUESTED =
  'notification.user_kyc_result_requested';

/** Payload properties of `userKycResultRequested`. */
export const USER_KYC_RESULT_FIELDS = [
  'userId',
  'recipientEmail',
  'organizationName',
  'result',
  'reason',
  'decidedAt',
] as const;

/** All of them but `reason`, which user-service leaves out for VERIFIED. */
export const USER_KYC_RESULT_REQUIRED = USER_KYC_RESULT_FIELDS.filter(
  (field) => field !== 'reason',
);

/**
 * user-service's command: one user, one address, no `payload.recipients`.
 * A REJECTED without a reason, or a VERIFIED with one, is what the contract
 * allows, so both parse; the template decides what to show.
 */
export function parseUserKycResultRequested(
  body: Buffer,
): UserKycResultRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    USER_KYC_RESULT_REQUESTED,
    'user-service',
  );
  payload.only(USER_KYC_RESULT_FIELDS);

  return {
    eventId: envelope.eventId,
    userId: payload.uuid('userId'),
    recipientEmail: payload.email('recipientEmail'),
    organizationName: payload.text('organizationName'),
    result: payload.enumValue('result', KYC_RESULTS),
    reason: payload.optionalText('reason'),
    decidedAt: payload.dateTime('decidedAt'),
  };
}

export const USER_LOCK_CHANGED_REQUESTED =
  'notification.user_lock_changed_requested';

/** Payload properties of `userLockChangedRequested`. */
export const USER_LOCK_CHANGED_FIELDS = [
  'userId',
  'recipientEmail',
  'action',
  'effectiveUntil',
  'reasonCode',
] as const;

/** All of them but `effectiveUntil`, which the contract leaves optional. */
export const USER_LOCK_CHANGED_REQUIRED = USER_LOCK_CHANGED_FIELDS.filter(
  (field) => field !== 'effectiveUntil',
);

/**
 * user-service's lock command: one user, one address. A LOCKED without an
 * end is what the contract allows (user-service never sends one), so it
 * parses; the template words it.
 */
export function parseUserLockChangedRequested(
  body: Buffer,
): UserLockChangedRequestedCommand {
  const { envelope, payload } = readCommand(
    body,
    USER_LOCK_CHANGED_REQUESTED,
    'user-service',
  );
  payload.only(USER_LOCK_CHANGED_FIELDS);

  return {
    eventId: envelope.eventId,
    userId: payload.uuid('userId'),
    recipientEmail: payload.email('recipientEmail'),
    action: payload.enumValue('action', LOCK_ACTIONS),
    effectiveUntil: payload.optionalNullableDateTime('effectiveUntil'),
    reasonCode: payload.text('reasonCode'),
  };
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** The JSON type of a bad value, never the value itself. */
function describe(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'an array';
  return `a ${typeof value}`;
}
