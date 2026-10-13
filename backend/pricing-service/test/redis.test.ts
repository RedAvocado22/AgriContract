/**
 * Review #157 (rules §4B): Redis is optional and must never block a request or startup.
 * These tests use the real node-redis client against servers that are down or stop answering.
 */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { dirname, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import type { PriceQuote } from '../src/domain/priceQuote.js';
import { CacheTimeoutError, createRedisCache, type RedisCacheHandle } from '../src/infrastructure/cache/redisPriceCache.js';
import { memoryLogger, postManual, startApp, validBody, type LogLine, type TestApp } from './fakes.js';

const serviceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const quote: PriceQuote = {
  id: 'q-1',
  commodity: 'COFFEE',
  itemName: 'Cà phê Robusta nhân xô',
  itemSlug: 'ca-phe-robusta-nhan-xo',
  province: 'Đắk Lắk',
  provinceSlug: 'dak-lak',
  price: '120500.00',
  source: 'ADMIN_MANUAL',
  capturedAt: '2026-10-10',
  ingestedAt: '2026-10-12T05:00:00.000Z',
  referenceSource: 'ref',
  reason: 'why',
  recordedBy: 'admin-1',
};

/** A port nobody listens on: connections are refused, like a stopped Redis. */
async function refusedPort(): Promise<number> {
  const server = net.createServer();
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  const { port } = server.address() as net.AddressInfo;
  await new Promise<void>((r) => server.close(() => r()));
  return port;
}

/**
 * Minimal RESP server. Answers like Redis (+OK, or a nil bulk for GET) until `muted`; once muted it
 * keeps reading but never answers, like a frozen Redis or a network partition.
 */
async function fakeRedis(muted: boolean) {
  const sockets = new Set<net.Socket>();
  const state = { muted, commands: [] as string[] };
  const server = net.createServer((socket) => {
    sockets.add(socket);
    socket.on('close', () => sockets.delete(socket));
    // Clients may vanish abruptly: on Windows a killed process resets its sockets (ECONNRESET).
    socket.on('error', () => {});
    let buf = '';
    socket.on('data', (chunk) => {
      buf += chunk.toString('latin1');
      for (;;) {
        const parsed = takeCommand(buf);
        if (!parsed) break;
        buf = parsed.rest;
        const name = (parsed.args[0] ?? '').toUpperCase();
        state.commands.push(name);
        if (!state.muted) socket.write(name === 'GET' ? '$-1\r\n' : '+OK\r\n');
      }
    });
  });
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  const { port } = server.address() as net.AddressInfo;
  return {
    url: `redis://127.0.0.1:${port}`,
    state,
    close: () =>
      new Promise<void>((r) => {
        for (const s of sockets) s.destroy();
        server.close(() => r());
      }),
  };
}

/** Parses one RESP array of bulk strings from the front of `buf`. */
function takeCommand(buf: string): { args: string[]; rest: string } | undefined {
  let pos = 0;
  const line = (): string | undefined => {
    const end = buf.indexOf('\r\n', pos);
    if (end < 0) return undefined;
    const text = buf.slice(pos, end);
    pos = end + 2;
    return text;
  };
  const head = line();
  if (head === undefined) return undefined;
  const args: string[] = [];
  for (let i = 0; i < Number(head.slice(1)); i++) {
    const len = line();
    if (len === undefined) return undefined;
    const size = Number(len.slice(1));
    if (buf.length < pos + size + 2) return undefined;
    args.push(buf.slice(pos, pos + size));
    pos += size + 2;
  }
  return { args, rest: buf.slice(pos) };
}

async function waitFor(check: () => boolean, ms: number): Promise<void> {
  const until = Date.now() + ms;
  while (!check()) {
    if (Date.now() > until) throw new Error('condition not reached in time');
    await new Promise((r) => setTimeout(r, 20));
  }
}

async function elapsed<T>(work: () => Promise<T>): Promise<{ ms: number; result?: T; error?: unknown }> {
  const t0 = performance.now();
  try {
    const result = await work();
    return { ms: performance.now() - t0, result };
  } catch (error) {
    return { ms: performance.now() - t0, error };
  }
}

describe('Redis cache never hangs (rules §4B)', () => {
  const cleanup: Array<() => Promise<void>> = [];
  afterEach(async () => {
    while (cleanup.length) await cleanup.pop()!();
  });

  function cacheFor(url: string, commandTimeoutMs: number, logs: LogLine[] = []): RedisCacheHandle {
    const handle = createRedisCache({ url, connectTimeoutMs: 200, commandTimeoutMs }, memoryLogger(logs));
    cleanup.push(handle.close);
    return handle;
  }

  it('Redis down (connection refused): the factory returns at once and an update fails immediately', async () => {
    const url = `redis://127.0.0.1:${await refusedPort()}`;
    const created = await elapsed(async () => cacheFor(url, 5000));
    assert.ok(created.ms < 200, `factory took ${created.ms} ms`);
    const put = await elapsed(() => created.result!.cache.putLatest(quote));
    assert.ok(put.error, 'the update must fail, not wait');
    assert.ok(!(put.error instanceof CacheTimeoutError), 'it fails at once (no offline queue), not after the timeout');
    assert.ok(put.ms < 1000, `update took ${put.ms} ms`);
  });

  it('Redis accepts TCP but never answers the handshake: an update fails immediately', async () => {
    const redis = await fakeRedis(true);
    cleanup.push(redis.close);
    const { cache } = cacheFor(redis.url, 5000);
    const put = await elapsed(() => cache.putLatest(quote));
    assert.ok(put.error);
    assert.ok(put.ms < 1000, `update took ${put.ms} ms`);
  });

  it('Redis stops answering after connecting: the update gives up after commandTimeoutMs', async () => {
    const redis = await fakeRedis(false);
    cleanup.push(redis.close);
    const logs: LogLine[] = [];
    const { cache } = cacheFor(redis.url, 300, logs);
    await waitFor(() => logs.some((l) => l.message === 'Redis connected'), 3000);
    await cache.putLatest(quote); // healthy Redis: GET + SET answered
    assert.deepEqual(redis.state.commands.slice(-2), ['GET', 'SET']);

    redis.state.muted = true;
    const put = await elapsed(() => cache.putLatest(quote));
    assert.ok(put.error instanceof CacheTimeoutError, String(put.error));
    assert.ok(put.ms >= 250 && put.ms < 1500, `gave up after ${put.ms} ms`);
  });

  describe('POST /api/v1/prices/manual with Redis unavailable', () => {
    let app: TestApp;
    afterEach(async () => {
      await app.close();
    });

    it('Redis down: 201 at once, row stored, warning logged', async () => {
      const url = `redis://127.0.0.1:${await refusedPort()}`;
      app = await startApp({ cache: cacheFor(url, 5000).cache });
      const res = await elapsed(() => postManual(app, validBody()));
      assert.equal(res.result?.status, 201);
      assert.ok(res.ms < 1000, `request took ${res.ms} ms`);
      assert.equal(app.repo.rows.length, 1);
      assert.ok(app.logs.some((l) => l.level === 'warn' && /Redis update failed/.test(l.message)));
    });

    it('Redis hung after connecting: 201 right after the cache timeout', async () => {
      const redis = await fakeRedis(false);
      cleanup.push(redis.close);
      const logs: LogLine[] = [];
      const handle = cacheFor(redis.url, 300, logs);
      await waitFor(() => logs.some((l) => l.message === 'Redis connected'), 3000);
      redis.state.muted = true;
      app = await startApp({ cache: handle.cache });
      const res = await elapsed(() => postManual(app, validBody()));
      assert.equal(res.result?.status, 201);
      assert.ok(res.ms < 1500, `request took ${res.ms} ms`);
      assert.equal(app.repo.rows.length, 1);
    });
  });

  describe('startup does not wait for Redis', () => {
    for (const scenario of ['connection refused', 'never answers'] as const) {
      it(`service answers /health while Redis is down (${scenario})`, { timeout: 30_000 }, async () => {
        let redisUrl: string;
        if (scenario === 'connection refused') {
          redisUrl = `redis://127.0.0.1:${await refusedPort()}`;
        } else {
          const redis = await fakeRedis(true);
          cleanup.push(redis.close);
          redisUrl = redis.url;
        }
        const port = await refusedPort();
        const child = spawn(process.execPath, ['--import', 'tsx', 'src/main.ts'], {
          cwd: serviceRoot,
          env: {
            ...process.env,
            GATEWAY_INTERNAL_SECRET: 'startup-test',
            SERVER_PORT: String(port),
            REDIS_URL: redisUrl,
            DB_HOST: '127.0.0.1',
            DB_PORT: String(await refusedPort()), // MySQL is not touched until a request needs it
          },
          stdio: 'ignore',
        });
        cleanup.push(
          () =>
            new Promise<void>((r) => {
              if (child.exitCode !== null) return r();
              child.once('exit', () => r());
              child.kill();
            }),
        );

        let status = 0;
        const deadline = Date.now() + 20_000;
        while (status !== 200 && Date.now() < deadline && child.exitCode === null) {
          status = await fetch(`http://127.0.0.1:${port}/health`).then(
            (r) => r.status,
            () => 0,
          );
          if (status !== 200) await new Promise((r) => setTimeout(r, 100));
        }
        assert.equal(status, 200, 'the service did not start while Redis was down');
      });
    }
  });
});
