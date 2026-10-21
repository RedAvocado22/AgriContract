import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { loadConfig } from '../config';

/**
 * Minimal Flyway-style runner: applies migrations/V<n>__*.sql in order, once each,
 * recording them in schema_migrations. Usage: npm run build && npm run migrate
 * (src/infrastructure/persistence and dist/infrastructure/persistence are both 3 levels below the service root.)
 */
const MIGRATIONS_DIR = join(__dirname, '..', '..', '..', 'migrations');

export function migrationFiles(dir: string = MIGRATIONS_DIR): string[] {
  return readdirSync(dir)
    .filter((f) => /^V\d+__.+\.sql$/.test(f))
    .sort((a, b) => Number(a.slice(1, a.indexOf('__'))) - Number(b.slice(1, b.indexOf('__'))));
}

/** A mysql2 connection opened with multipleStatements: true. */
export interface MigrationConnection {
  query(sql: string, values?: unknown[]): Promise<[unknown, unknown]>;
}

/** Applies every migration not yet recorded in schema_migrations; returns the files applied. */
export async function applyMigrations(conn: MigrationConnection, dir: string = MIGRATIONS_DIR): Promise<string[]> {
  await conn.query(
    `CREATE TABLE IF NOT EXISTS schema_migrations (
       version VARCHAR(255) NOT NULL PRIMARY KEY,
       applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
     )`,
  );
  const [rows] = await conn.query('SELECT version FROM schema_migrations');
  const applied = new Set((rows as Array<{ version: string }>).map((r) => r.version));
  const done: string[] = [];
  for (const file of migrationFiles(dir)) {
    if (applied.has(file)) continue;
    await conn.query(readFileSync(join(dir, file), 'utf8'));
    await conn.query('INSERT INTO schema_migrations (version) VALUES (?)', [file]);
    done.push(file);
  }
  return done;
}

async function run(): Promise<void> {
  const { db } = loadConfig();
  const { default: mysql } = await import('mysql2/promise');
  const conn = await mysql.createConnection({ ...db, multipleStatements: true });
  try {
    for (const file of await applyMigrations(conn)) console.info(`applied ${file}`);
  } finally {
    await conn.end();
  }
}

const norm = (p: string) => (process.platform === 'win32' ? resolve(p).toLowerCase() : resolve(p));
if (process.argv[1] && norm(__filename) === norm(process.argv[1])) {
  run().catch((err) => {
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  });
}
