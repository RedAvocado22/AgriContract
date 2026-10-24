/**
 * Deep equality that works across Jest's module sandbox. node:assert's deepStrictEqual also compares
 * prototypes, so an array or object built by Node itself (fs, fetch's response.json()) never equals a
 * literal written in a spec ("Compared values have no visual difference"). Jest's toEqual compares
 * structure and ignores that.
 */
export function deepEqual(actual: unknown, expected: unknown, message?: string): void {
  try {
    expect(actual).toEqual(expected);
  } catch (err) {
    if (message && err instanceof Error) err.message = `${message}\n${err.message}`;
    throw err;
  }
}
