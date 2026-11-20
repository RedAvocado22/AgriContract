import type { StoredFile } from '../../domain/stored-file';

export const FILE_REPOSITORY = Symbol('FILE_REPOSITORY');

/**
 * insert() found the (uploadedBy, idempotencyKey) pair already taken: another
 * store with the same key won the race (D60). Nothing was written.
 */
export class IdempotencyKeyTakenError extends Error {
  constructor() {
    super('Idempotency key already used by this uploader');
    this.name = IdempotencyKeyTakenError.name;
  }
}

/** The `file` table (file-service-phase2-design.md §8). */
export interface FileRepository {
  /**
   * Inserts a new row. Rejects with IdempotencyKeyTakenError when the row's
   * (uploadedBy, idempotencyKey) pair is already used; the fileId is new, so
   * any other duplicate is a bug and rejects as it is.
   */
  insert(file: StoredFile): Promise<void>;
  findById(fileId: string): Promise<StoredFile | null>;
  findByIdempotencyKey(
    uploadedBy: string,
    idempotencyKey: string,
  ): Promise<StoredFile | null>;
}
