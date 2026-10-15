import { afterEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../../src/infrastructure/http/app.js';

describe('GET /health', () => {
  let app: FastifyInstance | undefined;

  afterEach(async () => {
    await app?.close();
    app = undefined;
  });

  it('returns 200 when database and RabbitMQ are up', async () => {
    app = buildApp({ database: () => Promise.resolve(true), rabbitmq: () => true });

    const response = await app.inject({ method: 'GET', url: '/health' });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      status: 'UP',
      checks: { database: 'UP', rabbitmq: 'UP' },
    });
  });

  it('returns 503 when the database is down', async () => {
    app = buildApp({ database: () => Promise.resolve(false), rabbitmq: () => true });

    const response = await app.inject({ method: 'GET', url: '/health' });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({
      status: 'DOWN',
      checks: { database: 'DOWN', rabbitmq: 'UP' },
    });
  });

  it('returns 503 when RabbitMQ is down', async () => {
    app = buildApp({ database: () => Promise.resolve(true), rabbitmq: () => false });

    const response = await app.inject({ method: 'GET', url: '/health' });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({
      status: 'DOWN',
      checks: { database: 'UP', rabbitmq: 'DOWN' },
    });
  });
});
