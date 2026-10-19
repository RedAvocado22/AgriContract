import { Controller, HttpCode, Inject, Post, Req, Res, UseGuards } from '@nestjs/common';
import type { ServerResponse } from 'node:http';
import { recordManualPrice, type ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import type { PriceQuote } from '../../domain/priceQuote';
import { MANUAL_PRICE_DEPS } from '../nest/tokens';
import { HttpError } from './errors';
import { ManualPriceGuard, type ActorRequest } from './manualPrice.guard';
import { headerValue, readJsonBody, traceIdOf } from './request';
import { MANUAL_PRICE_PATH } from './routes';

/** POST /api/v1/prices/manual. Public read endpoints (GET /api/v1/prices/...) are a separate increment. */
@Controller()
export class PricesController {
  constructor(@Inject(MANUAL_PRICE_DEPS) private readonly deps: ManualPriceDeps) {}

  @Post(MANUAL_PRICE_PATH)
  @HttpCode(201)
  @UseGuards(ManualPriceGuard)
  async recordManual(@Req() req: ActorRequest, @Res({ passthrough: true }) res: ServerResponse): Promise<PriceQuote> {
    traceIdOf(req, res);
    if (!req.actor) throw new HttpError(403, 'GATEWAY_IDENTITY_REQUIRED', 'Missing authenticated user identity');
    const body = await readJsonBody(req);
    const result = await recordManualPrice(this.deps, req.actor, headerValue(req, 'idempotency-key'), body);
    if (result.replayed) res.setHeader('Idempotent-Replayed', 'true');
    return result.quote;
  }
}
