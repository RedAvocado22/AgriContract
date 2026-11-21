import type { Pool, RowDataPacket } from 'mysql2/promise';
import {
  IdempotencyKeyTakenError,
  type FileRepository,
} from '../../application/ports/file.repository';
import {
  FILE_STATUSES,
  INGEST_CHANNELS,
  type FileStatus,
  type IngestChannel,
  type StoredFile,
} from '../../domain/stored-file';

interface FileRow extends RowDataPacket {
  file_id: string;
  storage_hash: string;
  uploaded_by: string;
  ingest_channel: string;
  content_type: string;
  file_size: number | string;
  status: string;
  attached: number;
  idempotency_key: string | null;
}

const COLUMNS = `file_id, storage_hash, uploaded_by, ingest_channel, content_type,
              file_size, status, attached, idempotency_key`;

/** The unique index of V1 behind D60's one upload per (uploaded_by, idempotency_key). */
const IDEMPOTENCY_INDEX = 'uq_file_uploader_idempotency';

/** FileRepository on the `file` table of db/migration/V1__create_file.sql. */
export class FileMysqlRepository implements FileRepository {
  constructor(private readonly pool: Pool) {}

  async insert(file: StoredFile): Promise<void> {
    // created_at, retention and deletion columns take their table defaults.
    try {
      await this.pool.execute(
        `INSERT INTO file (${COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          file.fileId,
          file.storageHash,
          file.uploadedBy,
          file.ingestChannel,
          file.contentType,
          file.fileSize,
          file.status,
          file.attached,
          file.idempotencyKey,
        ],
      );
    } catch (err) {
      if (isDuplicateOn(err, IDEMPOTENCY_INDEX)) {
        throw new IdempotencyKeyTakenError();
      }
      throw err;
    }
  }

  async findById(fileId: string): Promise<StoredFile | null> {
    const [rows] = await this.pool.execute<FileRow[]>(
      `SELECT ${COLUMNS} FROM file WHERE file_id = ?`,
      [fileId],
    );
    return rows.length === 0 ? null : toStoredFile(rows[0]);
  }

  async findByIdempotencyKey(
    uploadedBy: string,
    idempotencyKey: string,
  ): Promise<StoredFile | null> {
    const [rows] = await this.pool.execute<FileRow[]>(
      `SELECT ${COLUMNS} FROM file
        WHERE uploaded_by = ? AND idempotency_key = ?`,
      [uploadedBy, idempotencyKey],
    );
    return rows.length === 0 ? null : toStoredFile(rows[0]);
  }
}

/**
 * MySQL names the violated key only in the message: "Duplicate entry '…' for
 * key 'file.uq_file_uploader_idempotency'" (8.0; 5.7 drops the table prefix).
 */
function isDuplicateOn(err: unknown, index: string): boolean {
  const e = err as { code?: unknown; sqlMessage?: unknown } | null;
  return (
    e?.code === 'ER_DUP_ENTRY' &&
    typeof e.sqlMessage === 'string' &&
    (e.sqlMessage.endsWith(`.${index}'`) ||
      e.sqlMessage.endsWith(`key '${index}'`))
  );
}

function toStoredFile(row: FileRow): StoredFile {
  return {
    fileId: row.file_id,
    storageHash: row.storage_hash,
    uploadedBy: row.uploaded_by,
    ingestChannel: oneOf(INGEST_CHANNELS, row.ingest_channel, 'ingest_channel'),
    contentType: row.content_type,
    // BIGINT comes back as a string when it exceeds 2^53; files never do.
    fileSize: Number(row.file_size),
    status: oneOf(FILE_STATUSES, row.status, 'status'),
    attached: row.attached === 1,
    idempotencyKey: row.idempotency_key,
  };
}

function oneOf<T extends FileStatus | IngestChannel>(
  values: readonly T[],
  value: string,
  column: string,
): T {
  if (!(values as readonly string[]).includes(value)) {
    throw new Error(`file.${column} holds an unknown value`);
  }
  return value as T;
}
