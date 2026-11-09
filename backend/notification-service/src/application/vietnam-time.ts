/*
 * Instants in Vietnam time for email bodies: every party of a contract reads
 * the time in Asia/Ho_Chi_Minh, next to the UTC instant the command carried.
 */

const VIETNAM_TIME = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** "2026-11-04 17:15:00" in Asia/Ho_Chi_Minh. */
export function vietnamTime(instant: Date): string {
  const part = Object.fromEntries(
    VIETNAM_TIME.formatToParts(instant).map((p) => [p.type, p.value]),
  );
  return `${part.year}-${part.month}-${part.day} ${part.hour}:${part.minute}:${part.second}`;
}
