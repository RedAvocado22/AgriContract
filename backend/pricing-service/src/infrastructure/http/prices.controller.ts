import { Controller, Get, HttpCode, Inject, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { ServerResponse } from 'node:http';
import { getPriceQuote, type PublicPriceQuote } from '../../application/usecase/getPriceQuote';
import { listPriceItems } from '../../application/usecase/listPriceItems';
import { recordManualPrice, type ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import type { PriceQuote } from '../../domain/priceQuote';
import { MANUAL_PRICE_DEPS, PRICE_READ_CONFIG } from '../nest/tokens';
import { HttpError } from './errors';
import { ManualPriceGuard, type ActorRequest } from './manualPrice.guard';
import { headerValue, readJsonBody, traceIdOf } from './request';
import { MANUAL_PRICE_PATH } from './routes';

/** Settings of the public read API only (design §2). */
export interface PriceReadConfig {
  /** A quote older than this many days (Vietnam date) is returned with stale = true. */
  staleThresholdDays: number;
}

/**
 * HTTP mapping only: path/query in, use-case result out. The rules live in application/usecase
 * (getPriceQuote, listPriceItems, recordManualPrice); errors become HTTP in errors.ts.
 */
@Controller()
export class PricesController {
  constructor(
    @Inject(MANUAL_PRICE_DEPS) private readonly deps: ManualPriceDeps,
    @Inject(PRICE_READ_CONFIG) private readonly readConfig: PriceReadConfig,
  ) {}

  /** Contract listPriceItems: 200 only. */
  @Get('/api/v1/prices/:commodity/:province/items')
  async listItems(@Param('commodity') commodity: string, @Param('province') province: string): Promise<unknown> {
    return { commodity, province, items: await listPriceItems(this.deps, commodity, province) };
  }

  /** Contract getPriceQuote: 200, or 404 PRICE_NOT_FOUND. */
  @Get('/api/v1/prices/:commodity/:province')
  getPrice(
    @Param('commodity') commodity: string,
    @Param('province') province: string,
    @Query('item') item?: string,
  ): Promise<PublicPriceQuote> {
    return getPriceQuote({ ...this.deps, staleThresholdDays: this.readConfig.staleThresholdDays }, commodity, province, item);
  }

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
