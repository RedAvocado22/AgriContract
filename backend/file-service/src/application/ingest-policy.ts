import type { IngestChannel } from '../domain/stored-file';

/** What an entrypoint accepts before a byte is stored (file-service-phase2-design.md §3). */
export interface IngestPolicy {
  channel: IngestChannel;
  /** Hard cap, checked against the declared length and again while streaming. */
  maxBytes: number;
  /** Accepted content types (normalized, see normalizeContentType); null = any. */
  contentTypes: readonly string[] | null;
}

const MiB = 1024 * 1024;

/**
 * DIRECT_UPLOAD (StoreOnBehalfOf, §3): JPG, PNG or PDF, at most 10MB
 * (decided 05/07/2026), read as 10 MiB. Virus scan follows (#131).
 */
export const DIRECT_UPLOAD_POLICY: IngestPolicy = {
  channel: 'DIRECT_UPLOAD',
  maxBytes: 10 * MiB,
  contentTypes: ['image/jpeg', 'image/png', 'application/pdf'],
};

/**
 * `Image/JPEG; charset=x` -> `image/jpeg`: parameters dropped, lower case.
 * Returns null for anything that is not a `type/subtype` token pair.
 */
export function normalizeContentType(raw: string): string | null {
  const value = raw.split(';', 1)[0].trim().toLowerCase();
  return /^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/.test(value) ? value : null;
}
