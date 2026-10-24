/** Phase 2 accepts COFFEE only (pricing-service-phase2-design.md §1, §2). */
export const COMMODITY = 'COFFEE' as const;
export type Commodity = typeof COMMODITY;

export const PRICE_SOURCES = ['VNSAT_SCRAPE', 'OPERATOR_MANUAL'] as const;
export type PriceSource = (typeof PRICE_SOURCES)[number];

/** A reference price (design §2). price_history in MySQL is the source of truth. */
export interface PriceQuote {
  id: string;
  commodity: Commodity;
  itemName: string;
  itemSlug: string;
  province: string;
  provinceSlug: string;
  price: string;
  source: PriceSource;
  capturedAt: string;
  ingestedAt: string;
  referenceSource: string | null;
  reason: string | null;
  recordedBy: string | null;
}

/**
 * Design §2: itemSlug = itemName without diacritics, lowercase, spaces -> dashes.
 * "Cà phê Robusta nhân xô" -> "ca-phe-robusta-nhan-xo"; "Đắk Lắk" -> "dak-lak".
 */
export function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
