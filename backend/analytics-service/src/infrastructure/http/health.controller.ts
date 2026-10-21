import { Controller, Get, Inject, ServiceUnavailableException } from '@nestjs/common';
import { AnalyticsLifecycle } from '../nest/analytics.lifecycle';

/**
 * GET /health: 200 UP once ingestion has started, 503 STARTING while MySQL or RabbitMQ is still being
 * waited for (and while shutting down). The HTTP server listens before any dependency is contacted,
 * so this endpoint never waits on them.
 */
@Controller('health')
export class HealthController {
  constructor(@Inject(AnalyticsLifecycle) private readonly lifecycle: Pick<AnalyticsLifecycle, 'isReady'>) {}

  @Get()
  health(): { status: 'UP' } {
    if (!this.lifecycle.isReady()) throw new ServiceUnavailableException({ status: 'STARTING' });
    return { status: 'UP' };
  }
}
