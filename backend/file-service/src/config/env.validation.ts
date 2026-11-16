/**
 * Environment of file-service. Defaults match the compose services (file-db on
 * 3317, object-storage on 8333 with the dev identity of
 * infra/seaweedfs/s3.json), so a local run needs no variable. Inject as
 * `ConfigService<AppConfig, true>` and read with `config.get('db', { infer: true })`.
 */
export interface AppConfig {
  db: {
    host: string;
    port: number;
    name: string;
    username: string;
    password: string;
  };
  /** S3-compatible object storage (file-service-phase2-design.md §2.3). */
  storage: {
    endpoint: string;
    region: string;
    bucket: string;
    accessKeyId: string;
    secretAccessKey: string;
  };
  serverPort: number;
}

/**
 * `ConfigModule.forRoot({ validate })` hook. A missing variable takes its
 * default; an empty value, a port outside 1–65535, an endpoint that is not an
 * http(s) URL or an invalid bucket name is an error. All errors are reported
 * together and startup fails.
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

  const endpoint = text('S3_ENDPOINT', 'http://localhost:8333');
  if (!/^https?:\/\/[^\s/]+(:\d+)?\/?$/.test(endpoint)) {
    errors.push(
      `S3_ENDPOINT must be an http(s) URL without a path, got "${endpoint}"`,
    );
  }
  const bucket = text('S3_BUCKET', 'agricontract-files');
  // S3 bucket naming rules, the subset every S3-compatible store accepts.
  if (!/^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(bucket)) {
    errors.push(
      `S3_BUCKET must be 3-63 lower-case letters, digits or hyphens, got "${bucket}"`,
    );
  }

  const config: AppConfig = {
    db: {
      host: text('DB_HOST', 'localhost'),
      port: port('DB_PORT', 3317),
      name: text('DB_NAME', 'file_db'),
      username: text('DB_USERNAME', 'root'),
      password: text('DB_PASSWORD', 'password'),
    },
    storage: {
      endpoint,
      region: text('S3_REGION', 'us-east-1'),
      bucket,
      accessKeyId: text('S3_ACCESS_KEY', 'agricontract'),
      secretAccessKey: text('S3_SECRET_KEY', 'agricontract-dev-secret'),
    },
    serverPort: port('SERVER_PORT', 8089),
  };

  if (errors.length > 0) {
    throw new Error(
      `Invalid environment configuration:\n  - ${errors.join('\n  - ')}`,
    );
  }
  return config;
}
