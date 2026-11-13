import { NonRetryableError } from '@agricontract/eventing';
import type {
  CategoryApprovedCommand,
  CategoryRejectedCommand,
} from '../../application/commands';

/*
 * Parsers for the flat Phase 1 events (D5): fields sit at the top level, as the
 * Java OutboxPollers publish them, with no envelope, so parseEnvelope is not
 * used. Every failure is a NonRetryableError, so the message goes to the DLQ
 * at once instead of being retried.
 *
 * Required fields (plan §4, intended change from Java, which only rejected a
 * wrong type and mailed "null" for a missing field): every field a template or
 * the dedup key reads must be a non-empty string; it is not trimmed or
 * converted. Optional fields may be absent or null, but if present must be
 * strings.
 *
 *   category.approved   eventId name proposedByEmail                 [categoryId]
 *   category.rejected   eventId name proposedByEmail rejectionReason [categoryId]
 *
 * categoryId is optional because no template uses it. rejectionReason is required:
 * product-service refuses a blank one.
 */

type Payload = Record<string, unknown>;

class PayloadReader {
  constructor(
    private readonly eventType: string,
    private readonly payload: Payload,
  ) {}

  required(field: string): string {
    const value = this.payload[field];
    if (typeof value !== 'string' || value === '') {
      throw this.error(
        `field "${field}" must be a non-empty string, got ${describe(value)}`,
      );
    }
    return value;
  }

  optional(field: string): string | null {
    const value = this.payload[field];
    if (value === undefined || value === null) return null;
    if (typeof value !== 'string') {
      throw this.error(
        `field "${field}" must be a string when present, got ${describe(value)}`,
      );
    }
    return value;
  }

  /** The message names the field and type, plus the eventId when it is a plain id. */
  error(reason: string): NonRetryableError {
    const id = this.payload.eventId;
    const event =
      typeof id === 'string' && SAFE_ID.test(id) ? ` (event ${id})` : '';
    return new NonRetryableError(
      `Malformed ${this.eventType} payload: ${reason}${event}`,
    );
  }
}

/** UUIDs and ids like evt-signed-1729: safe to log, unlike free text or emails. */
const SAFE_ID = /^[A-Za-z0-9-]{1,64}$/;

function read(eventType: string, body: Buffer | string): PayloadReader {
  const text = typeof body === 'string' ? body : body.toString('utf8');
  let payload: unknown;
  try {
    payload = JSON.parse(text);
  } catch {
    // Not the JSON.parse message: it quotes a slice of the payload, which may
    // hold names or emails (review #172).
    throw new NonRetryableError(
      `Malformed ${eventType} payload: not valid JSON`,
    );
  }
  if (
    typeof payload !== 'object' ||
    payload === null ||
    Array.isArray(payload)
  ) {
    throw new NonRetryableError(
      `Malformed ${eventType} payload: expected a JSON object, got ${describe(payload)}`,
    );
  }
  return new PayloadReader(eventType, payload as Payload);
}

/** Type name for error messages; never the value, which may be an email. */
function describe(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'an array';
  if (value === '') return 'an empty string';
  return typeof value;
}

export function parseCategoryApproved(
  body: Buffer | string,
): CategoryApprovedCommand {
  const p = read('category.approved', body);
  return {
    eventId: p.required('eventId'),
    categoryId: p.optional('categoryId'),
    name: p.required('name'),
    proposedByEmail: p.required('proposedByEmail'),
  };
}

export function parseCategoryRejected(
  body: Buffer | string,
): CategoryRejectedCommand {
  const p = read('category.rejected', body);
  return {
    eventId: p.required('eventId'),
    categoryId: p.optional('categoryId'),
    name: p.required('name'),
    proposedByEmail: p.required('proposedByEmail'),
    rejectionReason: p.required('rejectionReason'),
  };
}
