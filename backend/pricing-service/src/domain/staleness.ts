import { businessToday } from './manualPrice';

const DAY_MS = 86_400_000;

/**
 * Design §2: a quote is stale when its capturedAt is more than thresholdDays before today.
 * capturedAt is a Vietnam calendar date, so "today" is the Vietnam date too: comparing with the UTC
 * date would be one day off between 00:00 and 07:00 ICT.
 */
export function isStale(capturedAt: string, now: Date, thresholdDays: number): boolean {
  const captured = Date.parse(`${capturedAt}T00:00:00Z`);
  const today = Date.parse(`${businessToday(now)}T00:00:00Z`);
  return !Number.isFinite(captured) || Math.round((today - captured) / DAY_MS) > thresholdDays;
}
