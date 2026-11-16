import {
  Inject,
  Injectable,
  Logger,
  type FactoryProvider,
  type OnApplicationShutdown,
  type OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createPool, type Pool } from 'mysql2/promise';
import { failureCode } from '../../application/file-errors';
import type { AppConfig } from '../../config/env.validation';

/** Injection token of the shared `mysql2/promise` pool. */
export const MYSQL_POOL = Symbol('MYSQL_POOL');

export const mysqlPoolProvider: FactoryProvider<Pool> = {
  provide: MYSQL_POOL,
  inject: [ConfigService],
  useFactory: (config: ConfigService<AppConfig, true>) => {
    const db = config.get('db', { infer: true });
    return createPool({
      host: db.host,
      port: db.port,
      database: db.name,
      user: db.username,
      password: db.password,
      timezone: 'Z',
      connectionLimit: 10,
    });
  },
};

/**
 * Owns the pool lifecycle. Startup fails when the database is unreachable:
 * the app does not migrate (compose's file-migrate runs Flyway first).
 */
@Injectable()
export class MysqlService implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(MysqlService.name);

  constructor(
    @Inject(MYSQL_POOL) private readonly pool: Pool,
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  async ping(): Promise<void> {
    await this.pool.query('SELECT 1');
  }

  async onModuleInit(): Promise<void> {
    const db = this.config.get('db', { infer: true });
    const target = `${db.host}:${db.port}/${db.name}`;
    try {
      await this.ping();
    } catch (err) {
      throw new Error(
        `Cannot connect to MySQL at ${target} (${failureCode(err)})`,
      );
    }
    this.logger.log(`Connected to MySQL at ${target}`);
  }

  async onApplicationShutdown(): Promise<void> {
    await this.pool.end();
  }
}
