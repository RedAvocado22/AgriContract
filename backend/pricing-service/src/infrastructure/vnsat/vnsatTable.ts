import { load } from 'cheerio';

/** id of the result table on thitruongnongsan.gov.vn/vn/nguonwmy.aspx (design §3.1). */
export const VNSAT_TABLE_SELECTOR = '#ctl00_maincontent_GridView1';

/** One price row exactly as the VNSAT page shows it. Converting it to a PriceQuote is a separate step. */
export interface VnsatRow {
  /** "Tên mặt hàng", e.g. "Cà phê Robusta nhân xô". */
  itemName: string;
  /** "Thị trường", the province, e.g. "Đắk Lắk". */
  province: string;
  /** "Ngày" as dd-mm-yyyy, e.g. "30-10-2026". */
  date: string;
  /** "Giá" in VND/kg with "." as the thousands separator, e.g. "87.333". */
  price: string;
}

/** The page no longer looks like the layout this parser was written for (design §3.3, §5). */
export class VnsatLayoutChangedError extends Error {
  constructor(detail: string) {
    super(`VNSAT page layout changed: ${detail}`);
    this.name = 'VnsatLayoutChangedError';
  }
}

const COLUMNS = 4;

/**
 * Reads the price table out of a VNSAT result page.
 *  - a header row (only <th>) is skipped;
 *  - a single-cell row is VNSAT's "Không có dữ liệu." message, so the period simply has no prices;
 *  - anything else must have exactly 4 cells, otherwise the layout changed and we fail loudly
 *    instead of storing wrong prices.
 */
export function parseVnsatTable(html: string): VnsatRow[] {
  const $ = load(html);
  const table = $(VNSAT_TABLE_SELECTOR);
  if (table.length === 0) throw new VnsatLayoutChangedError(`table ${VNSAT_TABLE_SELECTOR} not found`);

  const rows: VnsatRow[] = [];
  table.find('tr').each((index, tr) => {
    const cells = $(tr)
      .find('td')
      .map((_, td) => $(td).text().trim())
      .get();
    if (cells.length === 0 || cells.length === 1) return;
    if (cells.length !== COLUMNS) {
      throw new VnsatLayoutChangedError(`row ${index} has ${cells.length} columns, expected ${COLUMNS}`);
    }
    const [itemName, province, date, price] = cells as [string, string, string, string];
    rows.push({ itemName, province, date, price });
  });
  return rows;
}
