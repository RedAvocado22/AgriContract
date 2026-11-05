import { VnsatLayoutChangedError, type VnsatRow } from './vnsatTable';

/** A VNSAT row in the formats price_history uses (design §2). */
export interface VnsatPrice {
  itemName: string;
  province: string;
  /** VND/kg with 2 decimals, e.g. "87333.00" (same shape as the manual entry). */
  price: string;
  /** Date the price applies, YYYY-MM-DD. */
  capturedAt: string;
}

/** "87.333", "950", "1.087.333": groups of 3 digits split by ".", no leading zero. */
const VN_PRICE = /^[1-9]\d{0,2}(\.\d{3})*$/;
/** price_history.price is DECIMAL(10,2): at most 8 digits before the decimal point. */
const MAX_DIGITS = 8;
const VN_DATE = /^(\d{2})-(\d{2})-(\d{4})$/;

export function toVnsatPrice(row: VnsatRow): VnsatPrice {
  return {
    itemName: row.itemName,
    province: row.province,
    price: vndPerKg(row.price),
    capturedAt: isoDate(row.date),
  };
}

/** "87.333" -> "87333.00". VNSAT writes whole VND with "." as the thousands separator. */
export function vndPerKg(text: string): string {
  if (!VN_PRICE.test(text)) throw new VnsatLayoutChangedError(`price "${text}" is not written like 87.333`);
  const digits = text.replace(/\./g, '');
  if (digits.length > MAX_DIGITS) throw new VnsatLayoutChangedError(`price "${text}" is above 99.999.999`);
  return `${digits}.00`;
}

/** "30-10-2026" -> "2026-10-30". Rejects dates that do not exist, such as 31-02-2026. */
export function isoDate(text: string): string {
  const match = VN_DATE.exec(text);
  if (!match) throw new VnsatLayoutChangedError(`date "${text}" is not written like 30-10-2026`);
  const [, day, month, year] = match as unknown as [string, string, string, string];
  const iso = `${year}-${month}-${day}`;
  const parsed = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== iso) {
    throw new VnsatLayoutChangedError(`date "${text}" does not exist`);
  }
  return iso;
}
