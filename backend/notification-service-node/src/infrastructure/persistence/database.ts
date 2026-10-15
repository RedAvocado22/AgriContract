import mysql from 'mysql2/promise';
import type { AppConfig } from '../../config.js';

export function createPool(config: AppConfig['db']): mysql.Pool {
  return mysql.createPool({
    host: config.host,
    port: config.port,
    database: config.name,
    user: config.username,
    password: config.password,
    connectionLimit: 10,
    connectTimeout: 3000,
  });
}

export async function pingDatabase(pool: mysql.Pool): Promise<boolean> {
  try {
    await pool.query('SELECT 1');
    return true;
  } catch {
    return false;
  }
}
