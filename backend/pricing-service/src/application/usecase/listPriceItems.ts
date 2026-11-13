import { COMMODITY, isSlug } from '../../domain/priceQuote';
import type { PriceHistoryRepository } from '../../domain/priceHistoryRepository';

/**
 * Use case "items with a reference price in a province" (design §4, contract listPriceItems, autocomplete).
 * The contract declares only 200: an unknown commodity or malformed province simply has no items.
 */
export async function listPriceItems(
  deps: { repository: PriceHistoryRepository },
  commodity: string,
  provinceSlug: string,
): Promise<Array<{ itemName: string; itemSlug: string }>> {
  if (commodity !== COMMODITY || !isSlug(provinceSlug)) return [];
  return deps.repository.listItems(COMMODITY, provinceSlug);
}
