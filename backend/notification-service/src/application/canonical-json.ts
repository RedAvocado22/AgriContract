/**
 * RFC 8785 JSON Canonicalization Scheme (JCS) of a parsed JSON value: the text
 * contract-service hashes into `signedContentHash` (`JcsTermsCanonicalizer`,
 * `org.erdtman.jcs`).
 *
 * JCS is defined on ECMAScript's own serialization, so on a value that came
 * out of `JSON.parse` it reduces to two rules:
 * - strings, numbers, booleans and null are written by `JSON.stringify`
 *   (RFC 8785 §3.2.2: number formatting and string escaping are ECMAScript's);
 * - object members are sorted by key in UTF-16 code unit order (§3.2.3), which
 *   is what `Array.prototype.sort` compares, recursively, with no whitespace.
 *
 * The numbers survive the round trip: contract-service canonicalizes through
 * IEEE-754 doubles as well, so every number in its canonical text is the
 * shortest form of a double, which `JSON.parse` reads back exactly.
 */
export function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(',')}]`;
  }
  if (value !== null && typeof value === 'object') {
    const object = value as Record<string, unknown>;
    const members = Object.keys(object)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalJson(object[key])}`);
    return `{${members.join(',')}}`;
  }
  if (typeof value === 'number' && !Number.isFinite(value)) {
    throw new TypeError('JCS has no form for a non-finite number');
  }
  if (
    value !== null &&
    typeof value !== 'string' &&
    typeof value !== 'number' &&
    typeof value !== 'boolean'
  ) {
    throw new TypeError(`JCS has no form for a ${typeof value}`);
  }
  return JSON.stringify(value);
}
