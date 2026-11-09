import { createHash } from 'node:crypto';

/*
 * RFC 8785 (JSON Canonicalization Scheme) §3.2.4 sample: input and its
 * canonical form, copied from the RFC. The hash is SHA-256 of the canonical
 * UTF-8 bytes, computed with `org.erdtman.jcs.JsonCanonicalizer` 1.1, the
 * library contract-service signs with.
 *
 * Plain escaped strings, not String.raw: TypeScript decodes some `\u` escapes
 * inside String.raw, which would change the sample.
 */
export const RFC8785_INPUT = [
  '{',
  '  "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],',
  '  "string": "\\u20ac$\\u000F\\u000aA\'\\u0042\\u0022\\u005c\\\\\\"\\/",',
  '  "literals": [null, true, false]',
  '}',
].join('\n');

export const RFC8785_OUTPUT =
  '{"literals":[null,true,false],"numbers":[333333333.3333333,1e+30,4.5,0.002,1e-27],' +
  '"string":"€$\\u000f\\nA\'B\\"\\\\\\\\\\"/"}';

export const RFC8785_HASH =
  '2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb';

export const sha256 = (text: string): string =>
  createHash('sha256').update(text, 'utf8').digest('hex');
