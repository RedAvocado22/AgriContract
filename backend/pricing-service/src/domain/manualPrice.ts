import { createHash } from 'node:crypto';
import { COMMODITY, slugify, type Commodity } from './priceQuote.js';

/**
 * Same money shape as the frozen API (askingUnitPrice): VND/kg with 2 decimals, max 99,999,999.99.
 * Canonical form only: no leading zeros ("0.50" is fine, "00100.00" is rejected). MySQL DECIMAL(10,2)
 * always hands the value back in canonical form, so accepting "00100.00" would make an idempotent
 * replay (read from MySQL) differ from the original response (error-catalog.md: a replay returns
 * the original successful response).
 */
const MONEY_PATTERN = /^(0|[1-9][0-9]{0,7})\.[0-9]{2}$/;
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

export const LIMITS = {
  itemName: 255,
  province: 100,
  referenceSource: 255,
  reason: 500,
} as const;

/** Body of POST /api/v1/prices/manual. */
export interface ManualPriceInput {
  commodity: Commodity;
  itemName: string;
  province: string;
  /** VND/kg, e.g. "120500.00". */
  price: string;
  /** Date the price applies (YYYY-MM-DD), per the referenced source. */
  capturedAt: string;
  /** Where the operator took the price from (newspaper, VNSAT page, association bulletin...). */
  referenceSource: string;
  /** Why the manual fallback was needed. */
  reason: string;
}

const INPUT_FIELDS = ['commodity', 'itemName', 'province', 'price', 'capturedAt', 'referenceSource', 'reason'] as const;

export type ValidationResult =
  | { ok: true; value: ManualPriceInput }
  | { ok: false; errors: string[] };

/**
 * Validates and normalises (trims) a manual price request.
 * `today` is the business date in Asia/Ho_Chi_Minh; prices cannot apply to a future date.
 */
export function validateManualPrice(body: unknown, today: string): ValidationResult {
  const errors: string[] = [];
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return { ok: false, errors: ['body must be a JSON object'] };
  }
  const b = body as Record<string, unknown>;

  for (const key of Object.keys(b)) {
    if (!(INPUT_FIELDS as readonly string[]).includes(key)) errors.push(`unknown field "${key}"`);
  }

  if (b.commodity !== COMMODITY) errors.push('commodity must be COFFEE (Phase 2 accepts coffee only)');

  const itemName = requiredText(b.itemName, 'itemName', LIMITS.itemName, errors);
  const province = requiredText(b.province, 'province', LIMITS.province, errors);
  const referenceSource = requiredText(b.referenceSource, 'referenceSource', LIMITS.referenceSource, errors);
  const reason = requiredText(b.reason, 'reason', LIMITS.reason, errors);
  if (itemName !== undefined && slugify(itemName) === '') errors.push('itemName must contain letters or digits');
  if (province !== undefined && slugify(province) === '') errors.push('province must contain letters or digits');

  if (typeof b.price !== 'string' || !MONEY_PATTERN.test(b.price)) {
    errors.push('price must be a string like "120500.00" (VND/kg, 2 decimals, no leading zeros, max 99999999.99)');
  } else if (Number(b.price) <= 0) {
    errors.push('price must be greater than 0');
  }

  if (typeof b.capturedAt !== 'string' || !isRealDate(b.capturedAt)) {
    errors.push('capturedAt must be a real date in YYYY-MM-DD format');
  } else if (b.capturedAt > today) {
    errors.push('capturedAt cannot be in the future');
  }

  if (errors.length > 0) return { ok: false, errors };
  return {
    ok: true,
    value: {
      commodity: COMMODITY,
      itemName: itemName!,
      province: province!,
      price: b.price as string,
      capturedAt: b.capturedAt as string,
      referenceSource: referenceSource!,
      reason: reason!,
    },
  };
}

/** Stable hash of the normalised request, used to detect Idempotency-Key reuse with a different body. */
export function requestHash(input: ManualPriceInput): string {
  const canonical = JSON.stringify(INPUT_FIELDS.map((k) => [k, input[k]]));
  return createHash('sha256').update(canonical, 'utf8').digest('hex');
}

/** Current calendar date (YYYY-MM-DD) in Vietnam business time. */
export function businessToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

function requiredText(value: unknown, field: string, max: number, errors: string[]): string | undefined {
  if (typeof value !== 'string' || value.trim().length === 0) {
    errors.push(`${field} is required`);
    return undefined;
  }
  const trimmed = value.trim();
  if (trimmed.length > max) {
    errors.push(`${field} must be at most ${max} characters`);
    return undefined;
  }
  return trimmed;
}

function isRealDate(value: string): boolean {
  const m = DATE_PATTERN.exec(value);
  if (!m) return false;
  const [, y, mo, d] = m;
  const year = Number(y);
  const month = Number(mo);
  const day = Number(d);
  if (month < 1 || month > 12 || day < 1) return false;
  return day <= new Date(Date.UTC(year, month, 0)).getUTCDate();
}
