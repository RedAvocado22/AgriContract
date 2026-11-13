/**
 * Environment of notification-service. Names and defaults are the ones the Java
 * service reads (application.yml), plus RABBITMQ_PORT and MAIL_FROM, so compose
 * does not change. Inject as `ConfigService<AppConfig, true>` and read with
 * `config.get('db', { infer: true })`.
 */
export interface AppConfig {
  db: {
    host: string;
    port: number;
    name: string;
    username: string;
    password: string;
  };
  rabbitmq: {
    host: string;
    port: number;
    username: string;
    password: string;
  };
  mail: { host: string; port: number; from: string };
  serverPort: number;
  /**
   * X-Internal-Secret of service-to-service calls (golden-flow-api.yaml
   * serviceAuth), the same SERVICE_INTERNAL_SECRET as the Java services.
   */
  serviceInternalSecret: string;
  /**
   * `notification.operatorEmail` (design §7): where the staff alerts go,
   * which name no recipient of their own (Level 1.5 inspection waiting for an
   * inspector, D49). Null when OPERATOR_EMAIL is absent or blank: the service
   * still starts, since every other email works without it, and each staff
   * alert is dead-lettered with a message that names the variable.
   */
  operatorEmail: string | null;
}

/** As loose as the address check of the command parsers: `format: email` decides the rest. */
const EMAIL = /^[^\s@]+@[^\s@]+$/;

/**
 * `ConfigModule.forRoot({ validate })` hook. A missing variable takes its
 * default; an empty value or a port outside 1–65535 is an error. All errors are
 * reported together and startup fails.
 */
export function validate(env: Record<string, unknown>): AppConfig {
  const errors: string[] = [];

  const text = (name: string, fallback: string): string => {
    const value = env[name];
    if (value === undefined) return fallback;
    if (typeof value !== 'string' || value.trim() === '') {
      errors.push(`${name} must not be empty`);
      return fallback;
    }
    return value;
  };

  const port = (name: string, fallback: number): number => {
    const value = env[name];
    if (value === undefined) return fallback;
    const raw =
      typeof value === 'string' ? value.trim() : JSON.stringify(value);
    const parsed = Number(raw);
    if (!/^\d+$/.test(raw) || parsed < 1 || parsed > 65535) {
      errors.push(`${name} must be an integer from 1 to 65535, got "${raw}"`);
      return fallback;
    }
    return parsed;
  };

  /** Absent or blank is null; anything else must look like one address. */
  const optionalEmail = (name: string): string | null => {
    const value = env[name];
    if (value === undefined) return null;
    if (typeof value !== 'string') {
      errors.push(`${name} must be an email address`);
      return null;
    }
    const trimmed = value.trim();
    if (trimmed === '') return null;
    if (!EMAIL.test(trimmed)) {
      errors.push(`${name} must be an email address`);
      return null;
    }
    return trimmed;
  };

  const config: AppConfig = {
    db: {
      host: text('DB_HOST', 'localhost'),
      port: port('DB_PORT', 3311),
      name: text('DB_NAME', 'notification_db'),
      username: text('DB_USERNAME', 'root'),
      password: text('DB_PASSWORD', 'password'),
    },
    rabbitmq: {
      host: text('RABBITMQ_HOST', 'localhost'),
      port: port('RABBITMQ_PORT', 5672),
      username: text('RABBITMQ_USERNAME', 'guest'),
      password: text('RABBITMQ_PASSWORD', 'guest'),
    },
    mail: {
      host: text('MAIL_HOST', 'localhost'),
      port: port('MAIL_PORT', 1025),
      from: text('MAIL_FROM', 'noreply@agricontract.com'),
    },
    serverPort: port('SERVER_PORT', 8085),
    // Same default as contract-service and user-service, so a local run
    // without .env still authenticates; compose requires it in .env.
    serviceInternalSecret: text('SERVICE_INTERNAL_SECRET', 'changeme-internal'),
    operatorEmail: optionalEmail('OPERATOR_EMAIL'),
  };

  if (errors.length > 0) {
    throw new Error(
      `Invalid environment configuration:\n  - ${errors.join('\n  - ')}`,
    );
  }
  return config;
}
