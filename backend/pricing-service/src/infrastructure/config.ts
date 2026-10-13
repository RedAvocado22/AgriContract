export interface DbConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
}

export interface RedisConfig {
  /** Optional: when unset the cache is disabled and prices live in MySQL only. */
  url: string | undefined;
  /** Max time to open the TCP connection before node-redis gives up and retries in the background. */
  connectTimeoutMs: number;
  /** Deadline for one cache update (GET + SET). Past it the request continues without the cache. */
  commandTimeoutMs: number;
}

export interface Config {
  port: number;
  db: DbConfig;
  gatewaySecret: string;
  redis: RedisConfig;
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
    redis: {
      url: env.REDIS_URL || undefined,
      connectTimeoutMs: positiveInt(env, 'REDIS_CONNECT_TIMEOUT_MS', 2000),
      commandTimeoutMs: positiveInt(env, 'REDIS_COMMAND_TIMEOUT_MS', 500),
    },
  };
}

function positiveInt(env: NodeJS.ProcessEnv, name: string, fallback: number): number {
  const raw = env[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer (milliseconds)`);
  return value;
}
