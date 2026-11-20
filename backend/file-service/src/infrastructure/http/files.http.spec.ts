import { createHash } from 'node:crypto';
import { Agent, request as httpRequest } from 'node:http';
import type { AddressInfo } from 'node:net';
import { Logger, type INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import type { App } from 'supertest/types';
import {
  FakeObjectStorage,
  InMemoryFileRepository,
} from '../../../test/support/fakes';
import { APP_OPTIONS } from '../../app.options';
import { StoreFileUseCase } from '../../application/store-file.use-case';
import { validate } from '../../config/env.validation';
import type { ErrorEnvelope } from './error-envelope.filter';
import { FilesController } from './files.controller';
import { InternalSecretGuard } from './internal-secret.guard';

const PATH = '/internal/v1/files';
const SECRET = 'test-internal-secret';
const USER = '0b6f3c1e-7d2a-4c5b-9e8f-1a2b3c4d5e6f';
const KEY = 'evidence-upload-1';
const MiB = 1024 * 1024;
const sha256 = (bytes: Buffer) =>
  createHash('sha256').update(bytes).digest('hex');

interface RawResponse {
  status: number;
  headers: Record<string, string | string[] | undefined>;
  body: Record<string, unknown>;
}

describe('POST /internal/v1/files (storeOnBehalfOf)', () => {
  let app: INestApplication;
  let storage: FakeObjectStorage;
  let files: InMemoryFileRepository;
  let logged: string[];
  let port: number;
  const pdf = Buffer.from('%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n');

  beforeEach(async () => {
    storage = new FakeObjectStorage();
    files = new InMemoryFileRepository();
    logged = [];
    for (const level of ['log', 'warn', 'error', 'debug'] as const) {
      jest
        .spyOn(Logger.prototype, level)
        .mockImplementation((...args: unknown[]) => {
          logged.push(args.map(String).join(' '));
        });
    }
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          validate: () => validate({ SERVICE_INTERNAL_SECRET: SECRET }),
        }),
      ],
      controllers: [FilesController],
      providers: [
        InternalSecretGuard,
        {
          provide: StoreFileUseCase,
          useFactory: () =>
            new StoreFileUseCase(storage, files, new Logger('test')),
        },
      ],
    }).compile();
    app = moduleRef.createNestApplication(APP_OPTIONS);
    await app.listen(0, '127.0.0.1');
    port = (app.getHttpServer() as { address(): AddressInfo }).address().port;
  });

  afterEach(async () => {
    await app.close();
    jest.restoreAllMocks();
  });

  const post = (body: Buffer = pdf, contentType = 'application/pdf') =>
    request(app.getHttpServer() as App)
      .post(PATH)
      .set('X-Internal-Secret', SECRET)
      .set('X-Acting-User-Id', USER)
      .set('Idempotency-Key', KEY)
      .set('Content-Type', contentType)
      .send(body);

  /**
   * A request written by hand, with its own headers. With leaveOpen, only
   * `send` is written and the request is never ended, so the caller is still
   * "sending" when the answer comes.
   */
  const raw = (
    headers: Record<string, string | number>,
    send: Buffer,
    { agent, leaveOpen = false }: { agent?: Agent; leaveOpen?: boolean } = {},
  ): Promise<RawResponse> =>
    new Promise((resolve, reject) => {
      const req = httpRequest({
        host: '127.0.0.1',
        port,
        method: 'POST',
        path: PATH,
        agent,
        headers: {
          'X-Internal-Secret': SECRET,
          'X-Acting-User-Id': USER,
          'Idempotency-Key': KEY,
          'Content-Type': 'application/pdf',
          ...headers,
        },
      });
      req.on('response', (res) => {
        const parts: Buffer[] = [];
        res.on('data', (c: Buffer) => parts.push(c));
        res.on('end', () =>
          resolve({
            status: res.statusCode ?? 0,
            headers: res.headers,
            body: JSON.parse(Buffer.concat(parts).toString('utf8')) as Record<
              string,
              unknown
            >,
          }),
        );
      });
      // A caller cut off mid-send may see its socket reset after the answer.
      req.on('error', (err) => {
        if (!leaveOpen) reject(err);
      });
      req.write(send);
      if (!leaveOpen) req.end();
    });

  const expectEnvelope = (
    status: number,
    code: string,
    got: { status: number; body: unknown },
  ) => {
    expect(got.status).toBe(status);
    const envelope = got.body as ErrorEnvelope;
    expect(Object.keys(envelope).sort()).toEqual(
      [
        'code',
        'error',
        'message',
        'path',
        'status',
        'timestamp',
        'traceId',
      ].sort(),
    );
    expect(envelope).toMatchObject({ status, code, path: PATH });
    return envelope;
  };

  const nothingKept = () => {
    expect(storage.objects.size).toBe(0);
    expect(files.rows.size).toBe(0);
  };

  describe('202', () => {
    it('stores the raw body and answers {fileId, status} only', async () => {
      const res = await post();

      expect(res.status).toBe(202);
      expect(Object.keys(res.body as object).sort()).toEqual([
        'fileId',
        'status',
      ]);
      const { fileId, status } = res.body as { fileId: string; status: string };
      expect(status).toBe('PROCESSING');
      expect(storage.objects.get(fileId)?.bytes).toEqual(pdf);
      expect(files.rows.get(fileId)).toEqual({
        fileId,
        storageHash: sha256(pdf),
        uploadedBy: USER,
        ingestChannel: 'DIRECT_UPLOAD',
        contentType: 'application/pdf',
        fileSize: pdf.length,
        status: 'PROCESSING',
        attached: false,
        idempotencyKey: KEY,
      });
    });

    it('stores X-Acting-User-Id in lower case, so the idempotency pair does not depend on case', async () => {
      const first = await post().set('X-Acting-User-Id', USER.toUpperCase());
      const second = await post();

      const fileId = (first.body as { fileId: string }).fileId;
      expect(files.rows.get(fileId)?.uploadedBy).toBe(USER);
      expect(second.body).toEqual(first.body);
    });

    it('takes image/jpeg, image/png and application/pdf', async () => {
      for (const [i, type] of [
        'image/jpeg',
        'image/png',
        'application/pdf',
      ].entries()) {
        const res = await post(pdf, type).set('Idempotency-Key', `k-${i}`);
        expect(res.status).toBe(202);
      }
      expect(files.rows.size).toBe(3);
    });

    it('takes a file of exactly 10 MiB', async () => {
      const res = await post(Buffer.alloc(10 * MiB, 7), 'image/png');

      expect(res.status).toBe(202);
      expect(storage.objects.size).toBe(1);
    });

    it('a repeat of (X-Acting-User-Id, Idempotency-Key) returns the same fileId and its current status, storing nothing', async () => {
      const first = await post();
      const { fileId } = first.body as { fileId: string };
      files.rows.set(fileId, { ...files.rows.get(fileId)!, status: 'READY' });

      const repeat = await post(Buffer.from('other bytes'));

      expect(repeat.status).toBe(202);
      expect(repeat.body).toEqual({ fileId, status: 'READY' });
      expect(storage.objects.size).toBe(1);
      expect(storage.objects.get(fileId)?.bytes).toEqual(pdf);
    });

    it('a repeat leaves the connection usable: its unread body is discarded', async () => {
      const agent = new Agent({ keepAlive: true, maxSockets: 1 });
      try {
        const body = Buffer.alloc(2 * MiB, 1);
        const first = await raw({ 'Content-Length': body.length }, body, {
          agent,
        });
        const repeat = await raw({ 'Content-Length': body.length }, body, {
          agent,
        });
        const other = await raw(
          { 'Content-Length': pdf.length, 'Idempotency-Key': 'next' },
          pdf,
          { agent },
        );

        expect(first.status).toBe(202);
        expect(repeat.body).toEqual(first.body);
        expect(other.status).toBe(202);
        expect(files.rows.size).toBe(2);
      } finally {
        agent.destroy();
      }
    });
  });

  describe('401', () => {
    it.each([
      ['no X-Internal-Secret', {}],
      ['a wrong X-Internal-Secret', { 'X-Internal-Secret': 'nope' }],
      [
        'X-Gateway-Secret instead',
        { 'X-Internal-Secret': '', 'X-Gateway-Secret': SECRET },
      ],
    ])('%s', async (_case, headers: Record<string, string>) => {
      const req = request(app.getHttpServer() as App)
        .post(PATH)
        .set('X-Acting-User-Id', USER)
        .set('Idempotency-Key', KEY)
        .set('Content-Type', 'application/pdf');
      for (const [name, value] of Object.entries(headers)) {
        if (value !== '') req.set(name, value);
      }

      expectEnvelope(401, 'UNAUTHORIZED', await req.send(pdf));
      nothingKept();
    });
  });

  describe('400 VALIDATION_ERROR', () => {
    it.each([
      ['X-Acting-User-Id missing', 'X-Acting-User-Id', null],
      ['X-Acting-User-Id not a UUID', 'X-Acting-User-Id', 'seller-1'],
      ['Idempotency-Key missing', 'Idempotency-Key', null],
      ['Idempotency-Key of 256 characters', 'Idempotency-Key', 'k'.repeat(256)],
    ])('%s', async (_case, header, value) => {
      const req = request(app.getHttpServer() as App)
        .post(PATH)
        .set('X-Internal-Secret', SECRET)
        .set('Content-Type', 'application/pdf');
      const headers: Record<string, string> = {
        'X-Acting-User-Id': USER,
        'Idempotency-Key': KEY,
      };
      if (value === null) delete headers[header];
      else headers[header] = value;
      for (const [name, v] of Object.entries(headers)) req.set(name, v);

      const envelope = expectEnvelope(
        400,
        'VALIDATION_ERROR',
        await req.send(pdf),
      );
      expect(envelope.message).toContain(header);
      if (value) expect(envelope.message).not.toContain(value);
      nothingKept();
    });

    it('takes an Idempotency-Key of 255 characters', async () => {
      const res = await post().set('Idempotency-Key', 'k'.repeat(255));
      expect(res.status).toBe(202);
    });

    it('reports every bad header at once', async () => {
      const res = await request(app.getHttpServer() as App)
        .post(PATH)
        .set('X-Internal-Secret', SECRET)
        .set('Content-Type', 'application/pdf')
        .send(pdf);

      const envelope = expectEnvelope(400, 'VALIDATION_ERROR', res);
      expect(envelope.message).toContain('X-Acting-User-Id');
      expect(envelope.message).toContain('Idempotency-Key');
    });

    it.each([
      ['a type outside the whitelist', 'image/gif'],
      ['a multipart form', 'multipart/form-data; boundary=x'],
    ])('Content-Type: %s', async (_case, type) => {
      expectEnvelope(400, 'VALIDATION_ERROR', await post(pdf, type));
      nothingKept();
    });

    it('no Content-Type', async () => {
      const res = await raw(
        { 'Content-Type': '', 'Content-Length': pdf.length },
        pdf,
      );
      expectEnvelope(400, 'VALIDATION_ERROR', res);
      nothingKept();
    });

    it('a chunked body (no Content-Length): refused unread, connection closed', async () => {
      const res = await raw({ 'Transfer-Encoding': 'chunked' }, pdf);

      const envelope = expectEnvelope(400, 'VALIDATION_ERROR', res);
      expect(envelope.message).toContain('Content-Length is required');
      expect(res.headers.connection).toBe('close');
      nothingKept();
    });

    it('an empty body', async () => {
      expectEnvelope(400, 'VALIDATION_ERROR', await post(Buffer.alloc(0)));
      nothingKept();
    });
  });

  describe('413 FILE_TOO_LARGE', () => {
    it('Content-Length over 10 MiB: answered while the caller is still sending, nothing read', async () => {
      // Declares 10 MiB + 1 and sends only the first 64 KiB.
      const res = await raw(
        { 'Content-Length': 10 * MiB + 1 },
        Buffer.alloc(64 * 1024),
        { leaveOpen: true },
      );

      const envelope = expectEnvelope(413, 'FILE_TOO_LARGE', res);
      expect(envelope.message).toBe(`File larger than ${10 * MiB} bytes`);
      expect(res.headers.connection).toBe('close');
      nothingKept();
    });

    it('a Content-Length past 2^53, which Node still parses', async () => {
      const res = await raw(
        { 'Content-Length': '9007199254740993' },
        Buffer.alloc(16),
        { leaveOpen: true },
      );
      expectEnvelope(413, 'FILE_TOO_LARGE', res);
    });
  });

  describe('503, nothing kept', () => {
    it('object storage down: FILE_STORAGE_UNAVAILABLE, code logged, no host leaked', async () => {
      storage.putError = Object.assign(
        new Error('connect ECONNREFUSED 10.0.0.5:8333'),
        { code: 'ECONNREFUSED' },
      );
      storage.failBeforeRead = true;

      const envelope = expectEnvelope(
        503,
        'FILE_STORAGE_UNAVAILABLE',
        await post(),
      );

      expect(JSON.stringify(envelope)).not.toContain('10.0.0.5');
      expect(logged.join('\n')).toContain('ECONNREFUSED');
      expect(logged.join('\n')).not.toContain('10.0.0.5');
      nothingKept();
    });

    it('object storage failing part way through the body: the 503 still reaches the caller, and the connection stays usable', async () => {
      storage.putError = Object.assign(new Error('socket hang up'), {
        code: 'ECONNRESET',
      });
      storage.failAfterBytes = 256 * 1024;
      const agent = new Agent({ keepAlive: true, maxSockets: 1 });
      try {
        const body = Buffer.alloc(4 * MiB, 3);
        const failed = await raw({ 'Content-Length': body.length }, body, {
          agent,
        });
        expectEnvelope(503, 'FILE_STORAGE_UNAVAILABLE', failed);
        nothingKept();

        storage.putError = null;
        const retry = await raw({ 'Content-Length': body.length }, body, {
          agent,
        });
        expect(retry.status).toBe(202);
        expect(files.rows.size).toBe(1);
      } finally {
        agent.destroy();
      }
    });

    it('the file database down: FILE_METADATA_UNAVAILABLE, the blob is removed', async () => {
      files.insertError = Object.assign(new Error('Too many connections'), {
        code: 'ER_CON_COUNT_ERROR',
      });

      expectEnvelope(503, 'FILE_METADATA_UNAVAILABLE', await post());
      expect(storage.objects.size).toBe(0);
      expect(storage.deleted).toHaveLength(1);
    });
  });

  /*
   * Answering before the body is in would make Node close the socket while
   * the caller is still sending, and the caller would see ECONNRESET instead
   * of the answer. The int-spec caught this with a 300 KiB body.
   */
  describe('a Connection: close caller sending 8 MiB gets the answer, not a reset', () => {
    const big = Buffer.alloc(8 * MiB, 5);
    const send = (headers: Record<string, string | number> = {}) =>
      raw(
        { Connection: 'close', 'Content-Length': big.length, ...headers },
        big,
      );

    it('401', async () => {
      expectEnvelope(
        401,
        'UNAUTHORIZED',
        await send({ 'X-Internal-Secret': 'x' }),
      );
    });

    it('400', async () => {
      expectEnvelope(
        400,
        'VALIDATION_ERROR',
        await send({ 'X-Acting-User-Id': 'not-a-uuid' }),
      );
    });

    it('400 for the content type', async () => {
      expectEnvelope(
        400,
        'VALIDATION_ERROR',
        await send({ 'Content-Type': 'image/gif' }),
      );
    });

    it('202 for a repeat', async () => {
      const first = await send();
      const repeat = await send();
      expect(repeat.status).toBe(202);
      expect(repeat.body).toEqual(first.body);
    });

    it('503 when storage fails part way', async () => {
      storage.putError = Object.assign(new Error('x'), { code: 'ECONNRESET' });
      storage.failAfterBytes = 128 * 1024;
      expectEnvelope(503, 'FILE_STORAGE_UNAVAILABLE', await send());
    });
  });

  it('never logs the acting user or the idempotency key', async () => {
    storage.putError = Object.assign(new Error('x'), { code: 'ECONNRESET' });
    storage.failBeforeRead = true;
    await post();
    files.insertError = new Error('db down');
    storage.putError = null;
    await post();

    const all = logged.join('\n');
    expect(all).not.toContain(USER);
    expect(all).not.toContain(KEY);
  });
});
