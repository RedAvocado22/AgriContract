import {
  Controller,
  Get,
  Inject,
  Injectable,
  Module,
  Res,
  ServiceUnavailableException,
} from '@nestjs/common';
import {
  HealthCheck,
  HealthCheckService,
  HealthIndicatorService,
  TerminusModule,
  type HealthCheckResult,
  type HealthIndicatorResult,
} from '@nestjs/terminus';
import type { Response } from 'express';
import { failureCode } from '../../application/file-errors';
import { OBJECT_STORAGE } from '../../application/ports/object-storage.port';
import { MysqlService } from '../persistence/mysql.provider';
import { PersistenceModule } from '../persistence/persistence.module';
import type { S3ObjectStorage } from '../storage/s3-object-storage';
import { StorageModule } from '../storage/storage.module';

/** A hung dependency must not hang the health endpoint. */
const CHECK_TIMEOUT_MS = 3_000;

async function withTimeout(check: () => Promise<void>): Promise<void> {
  let timer: NodeJS.Timeout | undefined;
  try {
    await Promise.race([
      check(),
      new Promise((_, reject) => {
        timer = setTimeout(
          () =>
            reject(
              Object.assign(
                new Error(`timed out after ${CHECK_TIMEOUT_MS}ms`),
                { code: 'HEALTH_CHECK_TIMEOUT' },
              ),
            ),
          CHECK_TIMEOUT_MS,
        );
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

@Injectable()
export class DependencyHealthIndicators {
  constructor(
    private readonly indicators: HealthIndicatorService,
    private readonly mysql: MysqlService,
    @Inject(OBJECT_STORAGE) private readonly storage: S3ObjectStorage,
  ) {}

  db(key: string) {
    return this.run(key, () => this.mysql.ping());
  }

  storageBucket(key: string) {
    return this.run(key, () => this.storage.ping());
  }

  private async run(key: string, check: () => Promise<void>) {
    const session = this.indicators.check(key);
    try {
      await withTimeout(check);
      return session.up();
    } catch (err) {
      // A code, not the message: S3 messages can quote the endpoint and bucket.
      return session.down({ error: failureCode(err) });
    }
  }
}

type SpringStatus = 'UP' | 'DOWN';

/** Spring Boot actuator shape, as the other services answer it. */
export interface ActuatorHealth {
  status: SpringStatus;
  components: Record<
    string,
    { status: SpringStatus } & Record<string, unknown>
  >;
}

/** GET /actuator/health: 200 when the database and the bucket are UP, 503 otherwise. */
@Controller('actuator')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly checks: DependencyHealthIndicators,
  ) {}

  @Get('health')
  @HealthCheck()
  async check(
    @Res({ passthrough: true }) res: Response,
  ): Promise<ActuatorHealth> {
    try {
      const result = await this.health.check([
        () => this.checks.db('db'),
        () => this.checks.storageBucket('storage'),
      ]);
      return toActuator('UP', result.details);
    } catch (err) {
      if (!(err instanceof ServiceUnavailableException)) throw err;
      res.status(503);
      return toActuator(
        'DOWN',
        (err.getResponse() as HealthCheckResult).details,
      );
    }
  }
}

function toActuator(
  status: SpringStatus,
  details: HealthIndicatorResult,
): ActuatorHealth {
  const components: ActuatorHealth['components'] = {};
  for (const [name, detail] of Object.entries(details)) {
    components[name] = {
      ...detail,
      status: detail.status === 'up' ? 'UP' : 'DOWN',
    };
  }
  return { status, components };
}

@Module({
  // Terminus would log an error on every failed check.
  imports: [
    TerminusModule.forRoot({ logger: false }),
    PersistenceModule,
    StorageModule,
  ],
  controllers: [HealthController],
  providers: [DependencyHealthIndicators],
})
export class HealthModule {}
