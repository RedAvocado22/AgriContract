export interface Config {
  port: number;
  db: { host: string; port: number; database: string; user: string; password: string };
  amqpUrl: string;
  prefetch: number;
}

/** Same environment variable names as the other services / docker-compose. */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const user = env.RABBITMQ_USERNAME ?? 'guest';
  const pass = env.RABBITMQ_PASSWORD ?? 'guest';
  const host = env.RABBITMQ_HOST ?? 'localhost';
  const port = env.RABBITMQ_PORT ?? '5672';
  return {
    port: Number(env.SERVER_PORT ?? 8093),
    db: {
      host: env.DB_HOST ?? 'localhost',
      port: Number(env.DB_PORT ?? 3306),
      database: env.DB_NAME ?? 'analytics_db',
      user: env.DB_USERNAME ?? 'root',
      password: env.DB_PASSWORD ?? '',
    },
    amqpUrl: env.AMQP_URL ?? `amqp://${encodeURIComponent(user)}:${encodeURIComponent(pass)}@${host}:${port}`,
    // Bounded in-flight messages per consumer: the backlog drains steadily after an outage
    // instead of flooding the DB with every buffered message at once.
    prefetch: Number(env.ANALYTICS_PREFETCH ?? 20),
  };
}
