/**
 * A readable reason for a connection failure. Connecting to "localhost" tries
 * ::1 and 127.0.0.1, and Node then throws an AggregateError with an empty
 * message, so list the inner errors; fall back to the code, then the name.
 */
export function describeError(err: unknown): string {
  if (err instanceof AggregateError && err.errors.length > 0) {
    return err.errors.map(describeError).join('; ');
  }
  if (err instanceof Error) {
    const code = (err as NodeJS.ErrnoException).code;
    return err.message || code || err.name;
  }
  return String(err);
}
