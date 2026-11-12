import { Controller, Get, HttpCode, Inject, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { ServerResponse } from 'node:http';
import { recordManualPrice, type ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import { COMMODITY, type PriceQuote } from '../../domain/priceQuote';
import { MANUAL_PRICE_DEPS } from '../nest/tokens';
import { HttpError } from './errors';
import { ManualPriceGuard, type ActorRequest } from './manualPrice.guard';
import { headerValue, readJsonBody, traceIdOf } from './request';
import { MANUAL_PRICE_PATH } from './routes';

/** Public advisory read endpoints plus the OPERATOR-only manual fallback write. */
@Controller()
export class PricesController {
  constructor(@Inject(MANUAL_PRICE_DEPS) private readonly deps: ManualPriceDeps) {}

  @Get('/api/v1/prices/:commodity/:province/items')
  async listItems(@Param('commodity') commodity: string, @Param('province') province: string): Promise<unknown> {
    assertCommodity(commodity);
    assertSlug(province, 'province');
    const items = await this.deps.repository.listItems(COMMODITY, province);
    return { commodity: COMMODITY, province, items };
  }

  @Get('/api/v1/prices/:commodity/:province')
  async getPrice(
    @Param('commodity') commodity: string,
    @Param('province') province: string,
    @Query('item') requestedItem?: string,
  ): Promise<unknown> {
    assertCommodity(commodity);
    assertSlug(province, 'province');
    if (requestedItem !== undefined) assertSlug(requestedItem, 'item');

    let item = requestedItem;
    if (!item) {
      const available = await this.deps.repository.listItems(COMMODITY, province);
      if (available.length === 0) throw new HttpError(404, 'PRICE_NOT_FOUND', 'No reference price is available');
      if (available.length > 1) throw new HttpError(400, 'PRICE_ITEM_REQUIRED', 'Specify the item query parameter');
      item = available[0]!.itemSlug;
    }

    let quote: PriceQuote | undefined;
    try {
      quote = await this.deps.cache.getLatest(province, item);
    } catch (err) {
      this.deps.logger.warn('Redis read failed; using MySQL price history', {
        province,
        item,
        error: err instanceof Error ? err.message : String(err),
      });
    }
    if (!quote) {
      quote = await this.deps.repository.findLatest(COMMODITY, province, item);
      if (!quote) throw new HttpError(404, 'PRICE_NOT_FOUND', 'No reference price is available');
      try {
        await this.deps.cache.putLatest(quote);
      } catch (err) {
        this.deps.logger.warn('Redis cache repair failed; MySQL price history remains available', {
          priceId: quote.id,
          error: err instanceof Error ? err.message : String(err),
        });
      }
    }

    const thresholdDays = this.deps.staleThresholdDays ?? 14;
    return {
      id: quote.id,
      commodity: quote.commodity,
      itemName: quote.itemName,
      itemSlug: quote.itemSlug,
      province: quote.province,
      provinceSlug: quote.provinceSlug,
      price: quote.price,
      source: quote.source,
      capturedAt: quote.capturedAt,
      ingestedAt: quote.ingestedAt,
      referenceSource: quote.referenceSource,
      reason: quote.reason,
      stale: isStale(quote.capturedAt, this.deps.now?.() ?? new Date(), thresholdDays),
    };
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

function assertCommodity(value: string): void {
  if (value !== COMMODITY) throw new HttpError(400, 'UNSUPPORTED_COMMODITY', 'Only COFFEE reference prices are supported');
}

function assertSlug(value: string, field: string): void {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value)) {
    throw new HttpError(400, 'VALIDATION_FAILED', `${field} must be a lowercase slug`);
  }
}

function isStale(capturedAt: string, now: Date, thresholdDays: number): boolean {
  const captured = Date.parse(`${capturedAt}T00:00:00Z`);
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return !Number.isFinite(captured) || Math.floor((today - captured) / 86_400_000) > thresholdDays;
}
