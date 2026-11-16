import type { Pool, RowDataPacket } from 'mysql2/promise';
import type { FileRepository } from '../../application/ports/file.repository';
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
}

/** FileRepository on the `file` table of db/migration/V1__create_file.sql. */
export class FileMysqlRepository implements FileRepository {
  constructor(private readonly pool: Pool) {}

  async insert(file: StoredFile): Promise<void> {
    // created_at, retention and deletion columns take their table defaults.
    await this.pool.execute(
      `INSERT INTO file (file_id, storage_hash, uploaded_by, ingest_channel,
                         content_type, file_size, status, attached)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        file.fileId,
        file.storageHash,
        file.uploadedBy,
        file.ingestChannel,
        file.contentType,
        file.fileSize,
        file.status,
        file.attached,
      ],
    );
  }

  async findById(fileId: string): Promise<StoredFile | null> {
    const [rows] = await this.pool.execute<FileRow[]>(
      `SELECT file_id, storage_hash, uploaded_by, ingest_channel, content_type,
              file_size, status, attached
         FROM file WHERE file_id = ?`,
      [fileId],
    );
    return rows.length === 0 ? null : toStoredFile(rows[0]);
  }
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
