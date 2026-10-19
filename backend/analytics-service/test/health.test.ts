/**
 * GET /health through a real Nest application (HealthController); only the readiness source is fake.
 * 503 STARTING while MySQL/RabbitMQ are still being waited for, 200 UP once ingestion has started.
 */
import 'reflect-metadata';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { after, before, describe, it } from 'node:test';
import { Module, type INestApplication } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { HealthController } from '../src/infrastructure/http/health.controller.js';
import { AnalyticsLifecycle } from '../src/infrastructure/nest/analytics.lifecycle.js';

let ready = false;

@Module({
  controllers: [HealthController],
  providers: [{ provide: AnalyticsLifecycle, useValue: { isReady: () => ready } }],
})
class HealthTestModule {}

describe('GET /health (Nest)', () => {
  let app: INestApplication;
  let base = '';

  before(async () => {
    app = await NestFactory.create(HealthTestModule, { logger: false });
    await app.listen(0, '127.0.0.1');
    base = `http://127.0.0.1:${((app.getHttpServer() as { address(): AddressInfo }).address()).port}`;
  });
  after(async () => {
    await app.close();
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
