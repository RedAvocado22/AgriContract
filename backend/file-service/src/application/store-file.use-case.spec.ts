import { createHash } from 'node:crypto';
import { Readable } from 'node:stream';
import {
  FakeObjectStorage,
  InMemoryFileRepository,
  lazyBody,
} from '../../test/support/fakes';
import {
  FileLengthMismatchError,
  FileMetadataUnavailableError,
  FileTooLargeError,
  InvalidFileLengthError,
  ObjectStorageUnavailableError,
  UnsupportedContentTypeError,
} from './file-errors';
import { DIRECT_UPLOAD_POLICY, type IngestPolicy } from './ingest-policy';
import { StoreFileUseCase, type StoreFileInput } from './store-file.use-case';

const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const MiB = 1024 * 1024;
const sha256 = (bytes: Buffer) =>
  createHash('sha256').update(bytes).digest('hex');

describe('StoreFileUseCase', () => {
  let storage: FakeObjectStorage;
  let files: InMemoryFileRepository;
  let logger: { warn: jest.Mock };
  let useCase: StoreFileUseCase;

  const pdf = Buffer.from('%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n');
  const input = (overrides: Partial<StoreFileInput> = {}): StoreFileInput => ({
    body: Readable.from([pdf]),
    contentType: 'application/pdf',
    contentLength: pdf.length,
    uploadedBy: 'seller-user-1',
    ...overrides,
  });

  beforeEach(() => {
    storage = new FakeObjectStorage();
    files = new InMemoryFileRepository();
    logger = { warn: jest.fn() };
    useCase = new StoreFileUseCase(storage, files, logger);
  });

  it('stores the bytes under a server-generated fileId and records the row PROCESSING', async () => {
    const file = await useCase.execute(input(), DIRECT_UPLOAD_POLICY);

    expect(file.fileId).toMatch(UUID);
    expect([...storage.objects.keys()]).toEqual([file.fileId]);
    expect(storage.objects.get(file.fileId)).toEqual({
      bytes: pdf,
      contentType: 'application/pdf',
      contentLength: pdf.length,
    });
    expect(files.rows.get(file.fileId)).toEqual({
      fileId: file.fileId,
      storageHash: sha256(pdf),
      uploadedBy: 'seller-user-1',
      ingestChannel: 'DIRECT_UPLOAD',
      contentType: 'application/pdf',
      fileSize: pdf.length,
      status: 'PROCESSING',
      attached: false,
    });
    expect(file).toEqual(files.rows.get(file.fileId));
  });

  it('gives every store its own fileId, even for the same bytes and uploader', async () => {
    const first = await useCase.execute(input(), DIRECT_UPLOAD_POLICY);
    const second = await useCase.execute(input(), DIRECT_UPLOAD_POLICY);

    expect(second.fileId).not.toBe(first.fileId);
    expect(second.storageHash).toBe(first.storageHash);
    expect(files.rows.size).toBe(2);
  });

  it('takes the channel from the policy of the entrypoint, never from the input', async () => {
    const policy: IngestPolicy = {
      channel: 'SYSTEM_GENERATED',
      maxBytes: MiB,
      contentTypes: null,
    };

    const file = await useCase.execute(
      input({ uploadedBy: 'contract-service', contentType: 'text/csv' }),
      policy,
    );

    expect(file.ingestChannel).toBe('SYSTEM_GENERATED');
    expect(file.contentType).toBe('text/csv');
  });

  /*
   * The acceptance criterion "streams without full buffering": storage gets
   * the first bytes while most of the body has not even been produced yet.
   */
  it('streams: storage receives bytes before the body has produced them all', async () => {
    const events: string[] = [];
    storage.onChunk = () => events.push('stored');
    const size = 10 * MiB;
    const body = lazyBody(size, 64 * 1024, () => events.push('produced'));

    const file = await useCase.execute(
      input({ body, contentLength: size }),
      DIRECT_UPLOAD_POLICY,
    );

    const produced = events.filter((e) => e === 'produced').length;
    expect(produced).toBe(160);
    const firstStored = events.indexOf('stored');
    const producedBeforeFirstStore = events
      .slice(0, firstStored)
      .filter((e) => e === 'produced').length;
    expect(producedBeforeFirstStore).toBeLessThan(produced / 2);
    expect(file.fileSize).toBe(size);
  });

  describe('refuses before reading a byte', () => {
    const untouched = () => {
      const body = lazyBody(MiB, 64 * 1024, () => {
        throw new Error('the body must not be read');
      });
      return body;
    };

    it.each([
      ['a type outside the whitelist', 'image/gif'],
      ['an HTML page', 'text/html'],
      ['a malformed type', 'pdf'],
      ['an empty type', ''],
    ])('rejects %s', async (_case, contentType) => {
      await expect(
        useCase.execute(
          input({ body: untouched(), contentType }),
          DIRECT_UPLOAD_POLICY,
        ),
      ).rejects.toBeInstanceOf(UnsupportedContentTypeError);
      expect(storage.objects.size).toBe(0);
      expect(files.rows.size).toBe(0);
    });

    it('accepts a type with parameters or in upper case, and stores it normalized', async () => {
      const file = await useCase.execute(
        input({ contentType: 'Application/PDF; charset=binary' }),
        DIRECT_UPLOAD_POLICY,
      );
      expect(file.contentType).toBe('application/pdf');
    });

    it('rejects a declared length above the 10 MiB cap', async () => {
      await expect(
        useCase.execute(
          input({ body: untouched(), contentLength: 10 * MiB + 1 }),
          DIRECT_UPLOAD_POLICY,
        ),
      ).rejects.toEqual(new FileTooLargeError(10 * MiB));
      expect(storage.objects.size).toBe(0);
    });

    it.each([0, -1, 1.5, Number.NaN, Number.MAX_SAFE_INTEGER + 1])(
      'rejects a declared length of %p',
      async (contentLength) => {
        await expect(
          useCase.execute(
            input({ body: untouched(), contentLength }),
            DIRECT_UPLOAD_POLICY,
          ),
        ).rejects.toBeInstanceOf(InvalidFileLengthError);
        expect(storage.objects.size).toBe(0);
      },
    );
  });

  describe('a body that does not match its declared length', () => {
    it('is cut off when it sends more, and leaves no blob and no row', async () => {
      const file = Buffer.alloc(2 * MiB);

      await expect(
        useCase.execute(
          input({ body: Readable.from([file]), contentLength: MiB }),
          DIRECT_UPLOAD_POLICY,
        ),
      ).rejects.toBeInstanceOf(FileLengthMismatchError);
      expect(storage.objects.size).toBe(0);
      expect(files.rows.size).toBe(0);
    });

    /*
     * The cap while streaming: a caller that declares 1 MiB and then sends
     * 50 MiB is stopped after the first chunk past 1 MiB, not after 50 MiB.
     */
    it('stops reading as soon as the body passes its declared length', async () => {
      let produced = 0;
      const body = lazyBody(50 * MiB, 64 * 1024, () => produced++);

      await expect(
        useCase.execute(
          input({ body, contentLength: MiB }),
          DIRECT_UPLOAD_POLICY,
        ),
      ).rejects.toBeInstanceOf(FileLengthMismatchError);
      // 16 chunks make 1 MiB; a few more may be in the pipe's buffers.
      expect(produced).toBeLessThan(40);
    });

    it('is refused when it ends early, and leaves no blob and no row', async () => {
      await expect(
        useCase.execute(
          input({ contentLength: pdf.length + 10 }),
          DIRECT_UPLOAD_POLICY,
        ),
      ).rejects.toEqual(
        new FileLengthMismatchError(pdf.length + 10, pdf.length),
      );
      expect(storage.objects.size).toBe(0);
      expect(files.rows.size).toBe(0);
    });

    it('is refused when the body itself fails (caller gone)', async () => {
      const body = new Readable({
        read() {
          this.destroy(
            Object.assign(new Error('aborted'), { code: 'ECONNRESET' }),
          );
        },
      });

      await expect(
        useCase.execute(input({ body }), DIRECT_UPLOAD_POLICY),
      ).rejects.toBeInstanceOf(Error);
      expect(storage.objects.size).toBe(0);
      expect(files.rows.size).toBe(0);
    });
  });

  describe('when a dependency fails', () => {
    it('object storage: 503-type error with a code only, no row', async () => {
      storage.putError = Object.assign(
        new Error('connect ECONNREFUSED 10.0.0.5:8333'),
        {
          code: 'ECONNREFUSED',
        },
      );
      storage.failBeforeRead = true;

      const err = await useCase
        .execute(input(), DIRECT_UPLOAD_POLICY)
        .catch((e: unknown) => e);

      expect(err).toEqual(new ObjectStorageUnavailableError('ECONNREFUSED'));
      expect((err as Error).message).not.toContain('10.0.0.5');
      expect(files.rows.size).toBe(0);
    });

    it('metadata: the stored blob is removed, so no blob is left without a row', async () => {
      files.insertError = Object.assign(new Error('Too many connections'), {
        code: 'ER_CON_COUNT_ERROR',
      });

      await expect(
        useCase.execute(input(), DIRECT_UPLOAD_POLICY),
      ).rejects.toEqual(new FileMetadataUnavailableError('ER_CON_COUNT_ERROR'));
      expect(storage.objects.size).toBe(0);
      expect(storage.deleted).toHaveLength(1);
    });

    it('metadata and the cleanup both: warns with the object key to delete by hand', async () => {
      files.insertError = new Error('db down');
      storage.deleteError = Object.assign(new Error('x'), {
        code: 'ECONNRESET',
      });

      await expect(
        useCase.execute(input(), DIRECT_UPLOAD_POLICY),
      ).rejects.toBeInstanceOf(FileMetadataUnavailableError);
      const [key] = storage.deleted;
      expect(logger.warn).toHaveBeenCalledTimes(1);
      expect(logger.warn).toHaveBeenCalledWith(
        expect.stringMatching(
          new RegExp(`\\(ECONNRESET\\); object ${key} has no row`),
        ),
      );
    });
  });
});
