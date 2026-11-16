import type { StoredFile } from '../../domain/stored-file';

export const FILE_REPOSITORY = Symbol('FILE_REPOSITORY');

/** The `file` table (file-service-phase2-design.md §8). */
export interface FileRepository {
  /** Inserts a new row; the fileId is new, so a duplicate is a bug and rejects. */
  insert(file: StoredFile): Promise<void>;
  findById(fileId: string): Promise<StoredFile | null>;
}
