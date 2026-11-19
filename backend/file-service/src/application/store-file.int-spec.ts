/**
 * The store core end to end on the compose services: real object storage
 * (SeaweedFS S3 API) and the real `file` table of db/migration. Run with
 *   npm run test:int
 * after `docker compose up -d --wait file-db object-storage` and
 * `docker compose up file-migrate` in backend/. DB_* and S3_* apply with the
 * usual defaults (localhost:3317, http://localhost:8333). Each run uses a
 * bucket of its own, deleted at the end.
 */
import { createHash, randomUUID } from 'node:crypto';
import { Readable } from 'node:stream';
import {
  DeleteBucketCommand,
  DeleteObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  S3Client,
} from '@aws-sdk/client-s3';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import { lazyBody } from '../../test/support/fakes';
import { validate } from '../config/env.validation';
import { FileMysqlRepository } from '../infrastructure/persistence/file.mysql-repository';
import {
  S3ObjectStorage,
  createS3Client,
} from '../infrastructure/storage/s3-object-storage';
import { FileLengthMismatchError } from './file-errors';
import { DIRECT_UPLOAD_POLICY } from './ingest-policy';
import { StoreFileUseCase } from './store-file.use-case';

const MiB = 1024 * 1024;
const config = validate(process.env);
const bucket = `file-int-${randomUUID().slice(0, 8)}`;
const sha256 = (bytes: Buffer) =>
  createHash('sha256').update(bytes).digest('hex');

describe('StoreFileUseCase on SeaweedFS and MySQL', () => {
  let client: S3Client;
  let storage: S3ObjectStorage;
  let pool: Pool;
  let useCase: StoreFileUseCase;
  const logger = { warn: jest.fn() };
  const fileIds: string[] = [];

  const objectBytes = async (key: string): Promise<Buffer> => {
    const res = await client.send(
      new GetObjectCommand({ Bucket: bucket, Key: key }),
    );
    return Buffer.from(await res.Body!.transformToByteArray());
  };
  const objectExists = async (key: string): Promise<boolean> =>
    client
      .send(new HeadObjectCommand({ Bucket: bucket, Key: key }))
      .then(() => true)
      .catch(() => false);
  const rowOf = async (fileId: string) => {
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT * FROM file WHERE file_id = ?',
      [fileId],
    );
    return rows;
  };
  const keys = async (): Promise<string[]> => {
    const res = await client.send(new ListObjectsV2Command({ Bucket: bucket }));
    return (res.Contents ?? []).map((o) => o.Key!);
  };

  /** Set once the bucket exists, so afterAll only cleans up what beforeAll made. */
  let bucketCreated = false;

  beforeAll(async () => {
    // Pool and client first: neither connects until used, so afterAll can
    // always close them, even when the storage below is not reachable.
    pool = createPool({
      host: config.db.host,
      port: config.db.port,
      database: config.db.name,
      user: config.db.username,
      password: config.db.password,
      timezone: 'Z',
    });
    client = createS3Client(config.storage);
    storage = new S3ObjectStorage(client, bucket);
    useCase = new StoreFileUseCase(
      storage,
      new FileMysqlRepository(pool),
      logger,
    );
    await expect(storage.ensureBucket()).resolves.toBe('CREATED');
    bucketCreated = true;
  });

  afterAll(async () => {
    try {
      if (fileIds.length > 0) {
        await pool.query('DELETE FROM file WHERE file_id IN (?)', [fileIds]);
      }
      if (bucketCreated) {
        for (const key of await keys()) {
          await client.send(
            new DeleteObjectCommand({ Bucket: bucket, Key: key }),
          );
        }
        await client.send(new DeleteBucketCommand({ Bucket: bucket }));
      }
    } finally {
      await pool.end(); // always, or Jest never exits
      client.destroy();
    }
  });

  it('ensureBucket is idempotent', async () => {
    await expect(storage.ensureBucket()).resolves.toBe('EXISTS');
  });

  it('stores a PDF under its fileId and records owner, channel, size and hash', async () => {
    const pdf = Buffer.from('%PDF-1.7\n% cadastral extract\n%%EOF\n');

    const file = await useCase.execute(
      {
        body: Readable.from([pdf]),
        contentType: 'application/pdf',
        contentLength: pdf.length,
        uploadedBy: 'seller-user-int',
      },
      DIRECT_UPLOAD_POLICY,
    );
    fileIds.push(file.fileId);

    expect(await objectBytes(file.fileId)).toEqual(pdf);
    const rows = await rowOf(file.fileId);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      file_id: file.fileId,
      storage_hash: sha256(pdf),
      uploaded_by: 'seller-user-int',
      ingest_channel: 'DIRECT_UPLOAD',
      content_type: 'application/pdf',
      file_size: pdf.length,
      status: 'PROCESSING',
      failure_reason: null,
      attached: 0,
      retention_until: null,
      legal_hold: 0,
      deleted_at: null,
      deletion_reason: null,
    });
    await expect(
      new FileMysqlRepository(pool).findById(file.fileId),
    ).resolves.toEqual(file);
  });

  it('streams a 10 MiB file, the largest DIRECT_UPLOAD accepts, byte for byte', async () => {
    const size = 10 * MiB;
    const expected = await new Promise<Buffer>((resolve, reject) => {
      const parts: Buffer[] = [];
      lazyBody(size)
        .on('data', (c: Buffer) => parts.push(c))
        .on('end', () => resolve(Buffer.concat(parts)))
        .on('error', reject);
    });

    const file = await useCase.execute(
      {
        body: lazyBody(size),
        contentType: 'image/png',
        contentLength: size,
        uploadedBy: 'buyer-user-int',
      },
      DIRECT_UPLOAD_POLICY,
    );
    fileIds.push(file.fileId);

    const stored = await objectBytes(file.fileId);
    expect(stored.length).toBe(size);
    expect(sha256(stored)).toBe(sha256(expected));
    expect(file.storageHash).toBe(sha256(expected));
  });

  it('leaves no object and no row when the body sends more than it declared', async () => {
    const before = await keys();

    await expect(
      useCase.execute(
        {
          body: lazyBody(3 * MiB),
          contentType: 'image/jpeg',
          contentLength: MiB,
          uploadedBy: 'seller-user-int',
        },
        DIRECT_UPLOAD_POLICY,
      ),
    ).rejects.toBeInstanceOf(FileLengthMismatchError);

    expect(await keys()).toEqual(before);
    const [rows] = await pool.query<RowDataPacket[]>(
      "SELECT COUNT(*) AS n FROM file WHERE uploaded_by = 'seller-user-int'",
    );
    expect(rows[0].n).toBe(1);
  });

  it('leaves no object and no row when the body ends early', async () => {
    const before = await keys();

    await expect(
      useCase.execute(
        {
          body: lazyBody(MiB),
          contentType: 'image/jpeg',
          contentLength: 2 * MiB,
          uploadedBy: 'short-body-int',
        },
        DIRECT_UPLOAD_POLICY,
      ),
    ).rejects.toBeInstanceOf(FileLengthMismatchError);

    expect(await keys()).toEqual(before);
    expect(await objectExists('missing')).toBe(false);
    const [rows] = await pool.query<RowDataPacket[]>(
      "SELECT COUNT(*) AS n FROM file WHERE uploaded_by = 'short-body-int'",
    );
    expect(rows[0].n).toBe(0);
  });
});
