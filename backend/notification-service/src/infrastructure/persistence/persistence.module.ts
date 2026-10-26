import { Module } from '@nestjs/common';
import { MYSQL_POOL, MysqlService, mysqlPoolProvider } from './mysql.provider';

@Module({
  providers: [mysqlPoolProvider, MysqlService],
  exports: [MYSQL_POOL, MysqlService],
})
export class PersistenceModule {}
