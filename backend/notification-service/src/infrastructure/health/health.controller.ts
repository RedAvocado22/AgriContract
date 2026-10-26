import {
  Controller,
  Get,
  Res,
  ServiceUnavailableException,
} from '@nestjs/common';
import {
  HealthCheck,
  HealthCheckService,
  type HealthCheckResult,
  type HealthIndicatorResult,
} from '@nestjs/terminus';
import type { Response } from 'express';
import { DatabaseHealthIndicator, RabbitHealthIndicator } from './indicators';

type SpringStatus = 'UP' | 'DOWN';

/** Spring Boot actuator shape: `{ status, components: { db: { status }, rabbit: { status } } }`. */
export interface ActuatorHealth {
  status: SpringStatus;
  components: Record<
    string,
    { status: SpringStatus } & Record<string, unknown>
  >;
}

/**
 * GET /actuator/health, kept at the Java path (D11). 200 when every component
 * is UP, 503 otherwise. Unlike the Spring service it does not check SMTP.
 */
@Controller('actuator')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly db: DatabaseHealthIndicator,
    private readonly rabbit: RabbitHealthIndicator,
  ) {}

  @Get('health')
  @HealthCheck()
  async check(
    @Res({ passthrough: true }) res: Response,
  ): Promise<ActuatorHealth> {
    try {
      const result = await this.health.check([
        () => this.db.check('db'),
        () => this.rabbit.check('rabbit'),
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
