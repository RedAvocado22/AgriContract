import type { ReferencePriceSource, ScrapedPrice } from '../../application/port';
import { toVnsatPrice } from './vnsatPrice';
import { DEFAULT_VNSAT_URL, scrapeVnsatCoffee, type VnsatFetch } from './vnsatScraper';

/** ReferencePriceSource adapter: the VNSAT WebForms scraper + table parser + VN number/date conversion. */
export class VnsatPriceSource implements ReferencePriceSource {
  constructor(
    readonly url: string = DEFAULT_VNSAT_URL,
    private readonly timeoutMs = 12_000,
    private readonly fetcher?: VnsatFetch,
  ) {}

  async fetchCoffeePrices(): Promise<ScrapedPrice[]> {
    const rows = await scrapeVnsatCoffee({ url: this.url, timeoutMs: this.timeoutMs, fetcher: this.fetcher });
    return rows.map(toVnsatPrice);
  }
}
