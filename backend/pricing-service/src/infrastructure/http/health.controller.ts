import { Controller, Get, Req, Res } from '@nestjs/common';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { traceIdOf } from './request';
import { HEALTH_PATH } from './routes';

/** Answers as soon as the HTTP server listens: it never waits for MySQL or Redis. */
@Controller()
export class HealthController {
  @Get(HEALTH_PATH)
  health(@Req() req: IncomingMessage, @Res({ passthrough: true }) res: ServerResponse): { status: 'UP' } {
    traceIdOf(req, res);
    return { status: 'UP' };
  }
}
