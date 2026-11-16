import { Module } from '@nestjs/common';
import type { Pool } from 'mysql2/promise';
import { FILE_REPOSITORY } from '../../application/ports/file.repository';
import { FileMysqlRepository } from './file.mysql-repository';
import { MYSQL_POOL, MysqlService, mysqlPoolProvider } from './mysql.provider';

@Module({
  providers: [
    mysqlPoolProvider,
    MysqlService,
    {
      provide: FILE_REPOSITORY,
      inject: [MYSQL_POOL],
      useFactory: (pool: Pool) => new FileMysqlRepository(pool),
    },
  ],
  exports: [MysqlService, FILE_REPOSITORY],
})
export class PersistenceModule {}
