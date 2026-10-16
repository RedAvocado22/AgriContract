import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { MysqlPoolLike } from '@agricontract/eventing';
import type { Pool } from 'mysql2/promise';
import { validate, type AppConfig } from '../../config/env.validation';
import { MysqlService } from './mysql.provider';

// Compile-time check: the mysql2 pool can be handed to @agricontract/eventing.
const asEventingPool = (pool: Pool): MysqlPoolLike => pool;
void asEventingPool;

function fakePool(query: jest.Mock) {
  const end = jest.fn().mockResolvedValue(undefined);
  return { pool: { query, end } as unknown as Pool, end };
}

const config = new ConfigService<AppConfig, true>(
  validate({ DB_HOST: 'db.test', DB_PORT: '3306', DB_PASSWORD: 'secret' }),
);

describe('MysqlService', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
  });

  it('pings with SELECT 1 on startup', async () => {
    const query = jest.fn().mockResolvedValue([[{ 1: 1 }], []]);
    const { pool } = fakePool(query);

    await new MysqlService(pool, config).onModuleInit();

    expect(query).toHaveBeenCalledWith('SELECT 1');
  });

  it('fails startup with the target but not the password when the database is unreachable', async () => {
    const query = jest
      .fn()
      .mockRejectedValue(new Error('connect ECONNREFUSED'));
    const { pool } = fakePool(query);

    const init = new MysqlService(pool, config).onModuleInit();

    await expect(init).rejects.toThrow(
      'Cannot connect to MySQL at db.test:3306/notification_db: connect ECONNREFUSED',
    );
    await expect(init).rejects.not.toThrow(/secret/);
  });

  it('ends the pool on shutdown', async () => {
    const { pool, end } = fakePool(jest.fn());

    await new MysqlService(pool, config).onModuleDestroy();

    expect(end).toHaveBeenCalledTimes(1);
  });
});
