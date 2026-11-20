/**
 * POST /internal/v1/files through the whole app (AppModule: config, guard,
 * MySQL, object storage) on the compose services. Run with
 *   npm run test:int
 * after `docker compose up -d --wait file-db object-storage` and
 * `docker compose up file-migrate` in backend/. DB_* and S3_* apply with the
 * usual defaults; the run uses a bucket of its own, deleted at the end.
 */
import { createHash, randomUUID } from 'node:crypto';
import {
  DeleteBucketCommand,
  DeleteObjectCommand,
  GetObjectCommand,
  ListObjectsV2Command,
  S3Client,
} from '@aws-sdk/client-s3';
import type { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import request from 'supertest';
import type { App } from 'supertest/types';
import { APP_OPTIONS } from '../../app.options';
import { validate } from '../../config/env.validation';
import { createS3Client } from '../storage/s3-object-storage';

const PATH = '/internal/v1/files';
const SECRET = `int-secret-${randomUUID()}`;
const bucket = `file-http-int-${randomUUID().slice(0, 8)}`;
const user = randomUUID();
const sha256 = (bytes: Buffer) =>
  createHash('sha256').update(bytes).digest('hex');

describe('POST /internal/v1/files on SeaweedFS and MySQL', () => {
  let app: INestApplication | undefined;
  let pool: Pool;
  let client: S3Client;
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    Buffer.alloc(300 * 1024, 9),
  ]);

  const post = (key: string, body: Buffer = png) =>
    request(app!.getHttpServer() as App)
      .post(PATH)
      .set('X-Internal-Secret', SECRET)
      .set('X-Acting-User-Id', user)
      .set('Idempotency-Key', key)
      .set('Content-Type', 'image/png')
      .send(body);

  beforeAll(async () => {
    // AppModule reads the environment when it is imported, so set it first.
    process.env.S3_BUCKET = bucket;
    process.env.SERVICE_INTERNAL_SECRET = SECRET;
    const config = validate(process.env);
    pool = createPool({
      host: config.db.host,
      port: config.db.port,
      database: config.db.name,
      user: config.db.username,
      password: config.db.password,
      timezone: 'Z',
    });
    client = createS3Client(config.storage);

    const { AppModule } = await import('../../app.module.js');
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication({
      ...APP_OPTIONS,
      logger: false,
    });
    await app.init(); // creates the bucket
  });

  afterAll(async () => {
    try {
      await pool.query('DELETE FROM file WHERE uploaded_by = ?', [user]);
      if (app) {
        const res = await client.send(
          new ListObjectsV2Command({ Bucket: bucket }),
        );
        for (const o of res.Contents ?? []) {
          await client.send(
            new DeleteObjectCommand({ Bucket: bucket, Key: o.Key! }),
          );
        }
        await client.send(new DeleteBucketCommand({ Bucket: bucket }));
      }
    } finally {
      await app?.close();
      await pool.end();
      client.destroy();
    }
  });

  it('202: the bytes are in the bucket under the fileId, the row is PROCESSING with uploader and key', async () => {
    const res = await post('http-int-1');

    expect(res.status).toBe(202);
    const { fileId, status } = res.body as { fileId: string; status: string };
    expect(status).toBe('PROCESSING');
    const object = await client.send(
      new GetObjectCommand({ Bucket: bucket, Key: fileId }),
    );
    expect(object.ContentType).toBe('image/png');
    expect(sha256(Buffer.from(await object.Body!.transformToByteArray()))).toBe(
      sha256(png),
    );
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT * FROM file WHERE file_id = ?',
      [fileId],
    );
    expect(rows[0]).toMatchObject({
      uploaded_by: user,
      idempotency_key: 'http-int-1',
      ingest_channel: 'DIRECT_UPLOAD',
      content_type: 'image/png',
      file_size: png.length,
      storage_hash: sha256(png),
      status: 'PROCESSING',
    });
  });

  it('a repeat answers the same fileId and current status, and writes nothing', async () => {
    const first = await post('http-int-2');
    const { fileId } = first.body as { fileId: string };
    await pool.query("UPDATE file SET status = 'READY' WHERE file_id = ?", [
      fileId,
    ]);

    const repeat = await post('http-int-2', Buffer.from('%PDF other'));

    expect(repeat.status).toBe(202);
    expect(repeat.body).toEqual({ fileId, status: 'READY' });
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT COUNT(*) AS n FROM file WHERE uploaded_by = ? AND idempotency_key = ?',
      [user, 'http-int-2'],
    );
    expect(rows[0].n).toBe(1);
  });

  it('401 with a wrong X-Internal-Secret, nothing written', async () => {
    const res = await post('http-int-3').set('X-Internal-Secret', 'wrong');

    expect(res.status).toBe(401);
    expect((res.body as { code: string }).code).toBe('UNAUTHORIZED');
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT COUNT(*) AS n FROM file WHERE idempotency_key = ?',
      ['http-int-3'],
    );
    expect(rows[0].n).toBe(0);
  });
});
