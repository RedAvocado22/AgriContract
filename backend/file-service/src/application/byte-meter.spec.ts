import { createHash } from 'node:crypto';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { ByteMeter } from './byte-meter';
import { FileLengthMismatchError } from './file-errors';

const sha256 = (bytes: Buffer) =>
  createHash('sha256').update(bytes).digest('hex');

async function drain(meter: ByteMeter, chunks: Buffer[]): Promise<Buffer> {
  const out: Buffer[] = [];
  await pipeline(Readable.from(chunks), meter, async (source) => {
    for await (const chunk of source as AsyncIterable<Buffer>) out.push(chunk);
  });
  return Buffer.concat(out);
}

describe('ByteMeter', () => {
  const bytes = Buffer.from('%PDF-1.7 a small evidence file');
  const chunks = [
    bytes.subarray(0, 5),
    bytes.subarray(5, 12),
    bytes.subarray(12),
  ];

  it('passes the bytes through unchanged, counts them and hashes them', async () => {
    const meter = new ByteMeter(bytes.length);

    await expect(drain(meter, chunks)).resolves.toEqual(bytes);

    expect(meter.bytes).toBe(bytes.length);
    expect(meter.sha256).toBe(sha256(bytes));
    expect(meter.sha256).toMatch(/^[a-f0-9]{64}$/);
  });

  it('fails at the first chunk past the declared length, without reading the rest', async () => {
    const meter = new ByteMeter(6);
    let read = 0;
    const source = Readable.from(
      (function* () {
        for (const chunk of chunks) {
          read++;
          yield chunk;
        }
        // Never reached: the second chunk already crosses the declared length.
        read += 100;
      })(),
    );

    await expect(
      pipeline(source, meter, async (s) => {
        for await (const _ of s) void _;
      }),
    ).rejects.toEqual(new FileLengthMismatchError(6, 12));
    expect(read).toBeLessThan(100);
  });

  /*
   * Storage commits once it has the declared Content-Length. A body longer
   * than declared must never get that many bytes through, or the object is
   * stored while the store fails (seen on SeaweedFS before the hold-back).
   */
  it('never lets a too-long body hand over the declared length', async () => {
    const declared = 12;
    const meter = new ByteMeter(declared);
    let passed = 0;

    await expect(
      pipeline(Readable.from(chunks), meter, async (source) => {
        for await (const chunk of source as AsyncIterable<Buffer>) {
          passed += chunk.length;
        }
      }),
    ).rejects.toBeInstanceOf(FileLengthMismatchError);
    expect(passed).toBeLessThan(declared);
  });

  it('holds back at most one chunk until the next one arrives', async () => {
    const meter = new ByteMeter(bytes.length);
    const seen: number[] = [];
    meter.on('data', (chunk: Buffer) => seen.push(chunk.length));

    meter.write(chunks[0]);
    await new Promise(setImmediate);
    expect(seen).toEqual([]);
    meter.write(chunks[1]);
    await new Promise(setImmediate);
    expect(seen).toEqual([5]);
    meter.end(chunks[2]);
    await new Promise((resolve) => meter.on('end', resolve));
    expect(seen).toEqual([5, 7, bytes.length - 12]);
  });

  it('fails at the end when the body is shorter than declared', async () => {
    const meter = new ByteMeter(bytes.length + 1);

    await expect(drain(meter, chunks)).rejects.toBeInstanceOf(
      FileLengthMismatchError,
    );
    expect(() => meter.sha256).toThrow('has not ended');
  });

  it('has no hash before the body has ended', () => {
    expect(() => new ByteMeter(1).sha256).toThrow('has not ended');
  });
});
