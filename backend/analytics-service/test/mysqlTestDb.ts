import { randomUUID } from 'node:crypto';
import mysql from 'mysql2/promise';
import { applyMigrations } from '../src/infrastructure/persistence/migrate';

/**
 * A private, empty database for one test suite, created from ANALYTICS_TEST_MYSQL_URL (the user in that URL
 * needs CREATE / DROP on the server). Every suite starts from nothing and applies all migrations itself,
 * so suites cannot race each other on the same tables and the suite also proves the migrations work on an
 * empty database. Call drop() in `after`.
 */
export interface TestDatabase {
  /** mysql:// URL of the private database. */
  url: string;
  drop(): Promise<void>;
}

export async function createTestDatabase(baseUrl: string, migrate = true): Promise<TestDatabase> {
  const parsed = new URL(baseUrl);
  const name = `analytics_it_${randomUUID().replace(/-/g, '').slice(0, 16)}`;
  const admin = await mysql.createConnection({ uri: baseUrl, multipleStatements: true });
  try {
    await admin.query(`CREATE DATABASE \`${name}\``);
  } finally {
    await admin.end();
  }
  parsed.pathname = `/${name}`;
  const url = parsed.toString();
  if (migrate) {
    const conn = await mysql.createConnection({ uri: url, multipleStatements: true });
    try {
      await applyMigrations(conn);
    } finally {
      await conn.end();
    }
  }
  return {
    url,
    async drop() {
      const conn = await mysql.createConnection({ uri: baseUrl });
      try {
        await conn.query(`DROP DATABASE IF EXISTS \`${name}\``);
      } finally {
        await conn.end();
      }
    },
  };
}
