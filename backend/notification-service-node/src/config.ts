export interface AppConfig {
  serverPort: number;
  db: {
    host: string;
    port: number;
    name: string;
    username: string;
    password: string;
  };
  rabbitmq: {
    host: string;
    username: string;
    password: string;
  };
  mail: {
    host: string;
    port: number;
  };
  adminEmail: string;
  serviceInternalSecret: string;
}

export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigError';
  }
}

const REQUIRED = ['DB_PASSWORD', 'SERVICE_INTERNAL_SECRET'] as const;

function readString(env: NodeJS.ProcessEnv, name: string, fallback: string): string {
  const value = env[name];
  return value === undefined || value === '' ? fallback : value;
}

function readPort(env: NodeJS.ProcessEnv, name: string, fallback: number): number {
  const raw = env[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const port = Number(raw);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigError(`${name} must be a TCP port number, got "${raw}"`);
  }
  return port;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const missing = REQUIRED.filter((name) => (env[name] ?? '') === '');
  if (missing.length > 0) {
    throw new ConfigError(`Missing required environment variables: ${missing.join(', ')}`);
  }

  return {
    serverPort: readPort(env, 'SERVER_PORT', 8085),
    db: {
      host: readString(env, 'DB_HOST', 'localhost'),
      port: readPort(env, 'DB_PORT', 3311),
      name: readString(env, 'DB_NAME', 'notification_node_db'),
      username: readString(env, 'DB_USERNAME', 'root'),
      password: env.DB_PASSWORD ?? '',
    },
    rabbitmq: {
      host: readString(env, 'RABBITMQ_HOST', 'localhost'),
      username: readString(env, 'RABBITMQ_USERNAME', 'guest'),
      password: readString(env, 'RABBITMQ_PASSWORD', 'guest'),
    },
    mail: {
      host: readString(env, 'MAIL_HOST', 'localhost'),
      port: readPort(env, 'MAIL_PORT', 1025),
    },
    adminEmail: readString(env, 'ADMIN_EMAIL', 'admin@agricontract.com'),
    serviceInternalSecret: env.SERVICE_INTERNAL_SECRET ?? '',
  };
}
