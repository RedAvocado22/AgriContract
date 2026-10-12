import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadConfig } from './config.js';

/**
 * Minimal Flyway-style runner: applies migrations/V<n>__*.sql in order, once each,
 * recording them in schema_migrations. Usage: npm run build && npm run migrate
 */
const MIGRATIONS_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'migrations');

export function migrationFiles(dir: string = MIGRATIONS_DIR): string[] {
  return readdirSync(dir)
    .filter((f) => /^V\d+__.+\.sql$/.test(f))
    .sort((a, b) => Number(a.slice(1, a.indexOf('__'))) - Number(b.slice(1, b.indexOf('__'))));
}

async function run(): Promise<void> {
  const { db } = loadConfig({ ...process.env, GATEWAY_INTERNAL_SECRET: process.env.GATEWAY_INTERNAL_SECRET ?? 'unused' });
  const { default: mysql } = await import('mysql2/promise');
  const conn = await mysql.createConnection({ ...db, multipleStatements: true });
  try {
    await conn.query(
      `CREATE TABLE IF NOT EXISTS schema_migrations (
         version VARCHAR(255) NOT NULL PRIMARY KEY,
         applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
       )`,
    );
    const [rows] = await conn.query('SELECT version FROM schema_migrations');
    const applied = new Set((rows as Array<{ version: string }>).map((r) => r.version));
    for (const file of migrationFiles()) {
      if (applied.has(file)) continue;
      await conn.query(readFileSync(join(MIGRATIONS_DIR, file), 'utf8'));
      await conn.query('INSERT INTO schema_migrations (version) VALUES (?)', [file]);
      console.info(`applied ${file}`);
    }
  } finally {
    await conn.end();
  }
}

const norm = (p: string) => (process.platform === 'win32' ? resolve(p).toLowerCase() : resolve(p));
if (process.argv[1] && norm(fileURLToPath(import.meta.url)) === norm(process.argv[1])) {
  run().catch((err) => {
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  });
}
