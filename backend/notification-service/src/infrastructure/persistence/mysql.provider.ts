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
import type { AppConfig } from '../../config/env.validation';
import { describeError } from '../describe-error';

/** Injection token of the shared `mysql2/promise` pool (assignable to eventing's `MysqlPoolLike`). */
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
      // DATETIME(6) columns hold UTC, as the Java service writes them (serverTimezone=UTC).
      timezone: 'Z',
      connectionLimit: 10,
    });
  },
};

/**
 * Owns the pool lifecycle. Startup fails when the database is unreachable,
 * as the Java service does (Flyway runs before it accepts traffic).
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
        `Cannot connect to MySQL at ${target}: ${describeError(err)}`,
      );
    }
    this.logger.log(`Connected to MySQL at ${target}`);
  }

  /**
   * Last shutdown step, after in-flight messages were drained
   * (NotificationEventConsumer.beforeApplicationShutdown) and still need the pool.
   */
  async onApplicationShutdown(): Promise<void> {
    await this.pool.end();
  }
}
