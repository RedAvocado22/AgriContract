import type { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { HealthIndicatorService, TerminusModule } from '@nestjs/terminus';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AmqpService } from '../messaging/amqp.provider';
import { MysqlService } from '../persistence/mysql.provider';
import { HealthController, type ActuatorHealth } from './health.controller';
import { DatabaseHealthIndicator, RabbitHealthIndicator } from './indicators';

describe('RabbitHealthIndicator', () => {
  const indicator = (healthy: boolean) =>
    new RabbitHealthIndicator(new HealthIndicatorService(), {
      isHealthy: () => healthy,
    } as AmqpService);

  it('is up while the channel is ready', () => {
    expect(indicator(true).check('rabbit')).toEqual({
      rabbit: { status: 'up' },
    });
  });

  it('is down without a channel', () => {
    expect(indicator(false).check('rabbit')).toEqual({
      rabbit: {
        status: 'down',
        error: 'no open channel with declared topology',
      },
    });
  });
});

describe('DatabaseHealthIndicator', () => {
  it('is down with the ping error', async () => {
    const indicator = new DatabaseHealthIndicator(
      new HealthIndicatorService(),
      { ping: () => Promise.reject(new Error('ECONNREFUSED')) } as MysqlService,
    );
    await expect(indicator.check('db')).resolves.toEqual({
      db: { status: 'down', error: 'ECONNREFUSED' },
    });
  });
});

describe('GET /actuator/health', () => {
  let app: INestApplication;
  const state = { dbUp: true, rabbitUp: true };

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [TerminusModule.forRoot({ logger: false })],
      controllers: [HealthController],
      providers: [
        DatabaseHealthIndicator,
        RabbitHealthIndicator,
        {
          provide: MysqlService,
          useValue: {
            ping: () =>
              state.dbUp
                ? Promise.resolve()
                : Promise.reject(new Error('down')),
          },
        },
        { provide: AmqpService, useValue: { isHealthy: () => state.rabbitUp } },
      ],
    }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.init();
  });

  afterAll(() => app.close());

  const getHealth = async () => {
    const res = await request(app.getHttpServer() as App).get(
      '/actuator/health',
    );
    return { status: res.status, body: res.body as ActuatorHealth };
  };

  it('answers 200 with status UP when everything is up', async () => {
    Object.assign(state, { dbUp: true, rabbitUp: true });
    const res = await getHealth();

    expect(res.status).toBe(200);
    expect(res.body).toEqual({
      status: 'UP',
      components: { db: { status: 'UP' }, rabbit: { status: 'UP' } },
    });
  });

  it('answers 503 with status DOWN when RabbitMQ is down', async () => {
    Object.assign(state, { dbUp: true, rabbitUp: false });
    const res = await getHealth();

    expect(res.status).toBe(503);
    expect(res.body).toEqual({
      status: 'DOWN',
      components: {
        db: { status: 'UP' },
        rabbit: {
          status: 'DOWN',
          error: 'no open channel with declared topology',
        },
      },
    });
  });

  it('answers 503 when the database is down', async () => {
    Object.assign(state, { dbUp: false, rabbitUp: true });
    const res = await getHealth();

    expect(res.status).toBe(503);
    expect(res.body.status).toBe('DOWN');
    expect(res.body.components.db).toEqual({ status: 'DOWN', error: 'down' });
  });
});
