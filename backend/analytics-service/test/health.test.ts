import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { after, before, describe, it } from 'node:test';
import { createHealthServer } from '../src/infrastructure/http/health.js';

describe('GET /health', () => {
  let ready = false;
  const server = createHealthServer(() => ready);
  let base = '';

  before(async () => {
    await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  after(async () => {
    await new Promise<void>((r) => server.close(() => r()));
  });

  it('503 STARTING while MySQL/RabbitMQ are still being waited for', async () => {
    ready = false;
    const res = await fetch(`${base}/health`);
    assert.equal(res.status, 503);
    assert.deepEqual(await res.json(), { status: 'STARTING' });
  });

  it('200 UP once ingestion has started', async () => {
    ready = true;
    const res = await fetch(`${base}/health`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { status: 'UP' });
  });

  it('404 for any other path', async () => {
    const res = await fetch(`${base}/nope`);
    assert.equal(res.status, 404);
  });
});
