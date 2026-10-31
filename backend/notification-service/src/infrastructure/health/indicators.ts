import { Injectable } from '@nestjs/common';
import { HealthIndicatorService } from '@nestjs/terminus';
import { AmqpService } from '../messaging/amqp.provider';
import { MysqlService } from '../persistence/mysql.provider';

/** A hung database must not hang the health endpoint. */
const DB_PING_TIMEOUT_MS = 3_000;

@Injectable()
export class DatabaseHealthIndicator {
  constructor(
    private readonly indicators: HealthIndicatorService,
    private readonly mysql: MysqlService,
  ) {}

  async check(key: string) {
    const session = this.indicators.check(key);
    let timer: NodeJS.Timeout | undefined;
    try {
      await Promise.race([
        this.mysql.ping(),
        new Promise((_, reject) => {
          timer = setTimeout(
            () =>
              reject(new Error(`ping timed out after ${DB_PING_TIMEOUT_MS}ms`)),
            DB_PING_TIMEOUT_MS,
          );
        }),
      ]);
      return session.up();
    } catch (err) {
      return session.down({
        error: err instanceof Error ? err.message : String(err),
      });
    } finally {
      clearTimeout(timer);
    }
  }
}

@Injectable()
export class RabbitHealthIndicator {
  constructor(
    private readonly indicators: HealthIndicatorService,
    private readonly amqp: AmqpService,
  ) {}

  check(key: string) {
    const session = this.indicators.check(key);
    return this.amqp.isHealthy()
      ? session.up()
      : session.down({ error: 'no open channel with declared topology' });
  }
}
