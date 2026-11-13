import { randomUUID } from 'node:crypto';
import { Inject, Injectable, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import type { Logger } from '../../application/port';
import type { ManualPriceDeps } from '../../application/usecase/recordManualPrice';
import type { Config } from '../config';
import { CONFIG, LOGGER, MANUAL_PRICE_DEPS } from '../nest/tokens';
import { runVnsatPriceIngestion } from './vnsatPriceIngestion';
import { scrapeVnsatCoffee } from './vnsatScraper';

/** Daily, non-blocking VNSAT ingestion. A failed source never removes a stored or cached quote. */
@Injectable()
export class VnsatPriceIngestionJob implements OnApplicationBootstrap, OnApplicationShutdown {
  private timer: NodeJS.Timeout | undefined;
  private running = false;

  constructor(
    @Inject(CONFIG) private readonly config: Config,
    @Inject(MANUAL_PRICE_DEPS) private readonly deps: ManualPriceDeps,
    @Inject(LOGGER) private readonly logger: Logger,
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

  async run(): Promise<void> {
    if (this.running) return;
    this.running = true;
    try {
      await runVnsatPriceIngestion(
        this.deps,
        this.logger,
        () => scrapeVnsatCoffee({ url: this.config.vnsat.url, timeoutMs: this.config.vnsat.timeoutMs }),
        new Date(),
        randomUUID,
        this.config.vnsat.url,
      );
    } finally {
      this.running = false;
    }
  }
}
