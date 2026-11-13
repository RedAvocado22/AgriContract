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

  /** Frozen contract (listPriceItems): 200 only. An unknown commodity or province simply has no items. */
  @Get('/api/v1/prices/:commodity/:province/items')
  async listItems(@Param('commodity') commodity: string, @Param('province') province: string): Promise<unknown> {
    const items = commodity === COMMODITY && isSlug(province)
      ? await this.deps.repository.listItems(COMMODITY, province)
      : [];
    return { commodity, province, items };
  }

  /**
   * Frozen contract (getPriceQuote): 200 or 404. Anything that cannot name exactly one stored quote,
   * whether a non-COFFEE commodity, a malformed slug or several items with no ?item=, is a 404.
   */
  @Get('/api/v1/prices/:commodity/:province')
  async getPrice(
    @Param('commodity') commodity: string,
    @Param('province') province: string,
    @Query('item') requestedItem?: string,
  ): Promise<unknown> {
    if (commodity !== COMMODITY || !isSlug(province) || (requestedItem !== undefined && !isSlug(requestedItem))) {
      throw notFound();
    }

    let item = requestedItem;
    if (!item) {
      const available = await this.deps.repository.listItems(COMMODITY, province);
      if (available.length === 0) throw notFound();
      if (available.length > 1) {
        throw new HttpError(404, 'PRICE_NOT_FOUND', 'Several items have prices for this province; pass ?item= (see /items)');
      }
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
      if (!quote) throw notFound();
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

function isSlug(value: string): boolean {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value);
}

function notFound(): HttpError {
  return new HttpError(404, 'PRICE_NOT_FOUND', 'No reference price is available');
}

function isStale(capturedAt: string, now: Date, thresholdDays: number): boolean {
  const captured = Date.parse(`${capturedAt}T00:00:00Z`);
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return !Number.isFinite(captured) || Math.floor((today - captured) / 86_400_000) > thresholdDays;
}
