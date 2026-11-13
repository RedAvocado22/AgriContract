import { Inject, Injectable, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import type { Logger, ReferencePriceSource } from '../../application/port';
import { ingestVnsatPrices } from '../../application/usecase/ingestVnsatPrices';
import type { ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import type { Config } from '../config';
import { CONFIG, LOGGER, MANUAL_PRICE_DEPS, VNSAT_SOURCE } from '../nest/tokens';

/**
 * Scheduler only (design §3.3): runs the ingestVnsatPrices use case at start-up and then every interval,
 * without blocking start-up. A run that is still going is never overlapped by the next tick.
 */
@Injectable()
export class VnsatPriceIngestionJob implements OnApplicationBootstrap, OnApplicationShutdown {
  private timer: NodeJS.Timeout | undefined;
  private running = false;

  constructor(
    @Inject(CONFIG) private readonly config: Config,
    @Inject(MANUAL_PRICE_DEPS) private readonly deps: ManualPriceDeps,
    @Inject(LOGGER) private readonly logger: Logger,
    @Inject(VNSAT_SOURCE) private readonly source: ReferencePriceSource,
  ) {}

  onApplicationBootstrap(): void {
    if (!this.config.vnsat.enabled) {
      this.logger.info('VNSAT scraper disabled by configuration');
      return;
    }
    void this.run();
    this.timer = setInterval(() => void this.run(), this.config.vnsat.intervalMs);
  }

  onApplicationShutdown(): void {
    if (this.timer) clearInterval(this.timer);
  }

  /** Returns false when skipped because the previous run has not finished. */
  async run(): Promise<boolean> {
    if (this.running) {
      this.logger.warn('VNSAT ingestion still running; skipping this tick');
      return false;
    }
    this.running = true;
    try {
      await ingestVnsatPrices({ ...this.deps, logger: this.logger, source: this.source });
      return true;
    } finally {
      this.running = false;
    }
  }
}
