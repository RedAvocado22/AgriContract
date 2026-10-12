export interface Config {
  port: number;
  db: { host: string; port: number; database: string; user: string; password: string };
  gatewaySecret: string;
  redisUrl: string | undefined;
}

/** Same environment variable names as the Java services / docker-compose. */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const gatewaySecret = env.GATEWAY_INTERNAL_SECRET;
  if (!gatewaySecret) throw new Error('GATEWAY_INTERNAL_SECRET must be set');
  return {
    port: Number(env.SERVER_PORT ?? 8091),
    db: {
      host: env.DB_HOST ?? 'localhost',
      port: Number(env.DB_PORT ?? 3306),
      database: env.DB_NAME ?? 'pricing_db',
      user: env.DB_USERNAME ?? 'root',
      password: env.DB_PASSWORD ?? '',
    },
    gatewaySecret,
    redisUrl: env.REDIS_URL || undefined,
  };
}
