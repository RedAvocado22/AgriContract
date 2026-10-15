import type { FastifyInstance } from 'fastify';

export interface HealthChecks {
  database: () => Promise<boolean>;
  rabbitmq: () => boolean;
}

export function registerHealthRoute(app: FastifyInstance, checks: HealthChecks): void {
  app.get('/health', async (_request, reply) => {
    const database = await checks.database();
    const rabbitmq = checks.rabbitmq();
    const healthy = database && rabbitmq;

    return reply.code(healthy ? 200 : 503).send({
      status: healthy ? 'UP' : 'DOWN',
      checks: {
        database: database ? 'UP' : 'DOWN',
        rabbitmq: rabbitmq ? 'UP' : 'DOWN',
      },
    });
  });
}
