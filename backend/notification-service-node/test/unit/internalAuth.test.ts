import { describe, expect, it } from 'vitest';
import { isValidInternalSecret } from '../../src/infrastructure/http/internalAuth.js';

describe('isValidInternalSecret', () => {
  it('accepts the exact secret', () => {
    expect(isValidInternalSecret('s3cret', 's3cret')).toBe(true);
  });

  it.each([
    ['missing', undefined],
    ['empty', ''],
    ['different', 's3creT'],
    ['shorter', 's3cre'],
    ['longer', 's3cret!'],
    ['repeated header', ['s3cret', 's3cret']],
  ])('rejects a %s value without throwing', (_name, provided) => {
    expect(isValidInternalSecret(provided, 's3cret')).toBe(false);
  });

  it('rejects everything when no secret is configured', () => {
    expect(isValidInternalSecret('', '')).toBe(false);
  });
});
