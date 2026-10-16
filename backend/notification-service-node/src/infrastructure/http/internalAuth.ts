import { createHash, timingSafeEqual } from 'node:crypto';

export const INTERNAL_SECRET_HEADER = 'x-internal-secret';

function digest(value: string): Buffer {
  return createHash('sha256').update(value, 'utf8').digest();
}

/**
 * Constant-time check of X-Internal-Secret against SERVICE_INTERNAL_SECRET. Both sides are
 * hashed first so timingSafeEqual always gets equal-length buffers and the comparison time
 * does not reveal the expected length.
 */
export function isValidInternalSecret(provided: unknown, expected: string): boolean {
  if (typeof provided !== 'string' || provided === '' || expected === '') {
    return false;
  }
  return timingSafeEqual(digest(provided), digest(expected));
}
