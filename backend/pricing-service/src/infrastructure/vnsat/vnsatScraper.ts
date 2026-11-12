import { load } from 'cheerio';
import { parseVnsatTable, VnsatLayoutChangedError, type VnsatRow } from './vnsatTable';

export const DEFAULT_VNSAT_URL = 'https://thitruongnongsan.gov.vn/vn/nguonwmy.aspx';
const DATE_FROM = 'ctl00$maincontent$tu_ngay';
const DATE_TO = 'ctl00$maincontent$den_ngay';
const COMMODITY = 'ctl00$maincontent$Ngành_hàng';
const PERIOD = 'ctl00$maincontent$Theo_thời_gian';
const SUBMIT = 'ctl00$maincontent$Xem';

interface ResponseLike {
  ok: boolean;
  status: number;
  headers: { get(name: string): string | null };
  text(): Promise<string>;
}

export type VnsatFetch = (url: string, init?: RequestInit) => Promise<ResponseLike>;

/** Performs a cookie-preserving ASP.NET WebForms GET + full-form POST for daily coffee prices. */
export async function scrapeVnsatCoffee(
  options: { url?: string; timeoutMs?: number; fetcher?: VnsatFetch; now?: Date } = {},
): Promise<VnsatRow[]> {
  const url = options.url ?? DEFAULT_VNSAT_URL;
  const timeoutMs = options.timeoutMs ?? 12_000;
  const fetcher = options.fetcher ?? (fetch as unknown as VnsatFetch);
  const get = await fetcher(url, {
    method: 'GET',
    headers: { 'User-Agent': 'AgriContract-PricingService/1.0 (+non-commercial research)' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!get.ok) throw new Error(`VNSAT page GET returned HTTP ${get.status}`);
  const html = await get.text();
  const $ = load(html);
  const form = $('form#aspnetForm').first();
  if (!form.length) throw new VnsatLayoutChangedError('ASP.NET form #aspnetForm not found');

  const fields = new URLSearchParams();
  form.find('input[name]').each((_, el) => {
    const input = $(el);
    const type = (input.attr('type') ?? 'text').toLowerCase();
    if (['submit', 'button', 'image', 'reset'].includes(type)) return;
    if (['checkbox', 'radio'].includes(type) && !input.is(':checked')) return;
    fields.append(input.attr('name')!, input.attr('value') ?? '');
  });
  form.find('select[name]').each((_, el) => {
    const select = $(el);
    const selected = select.find('option:selected').first();
    const option = selected.length ? selected : select.find('option').first();
    if (option.length) fields.append(select.attr('name')!, option.attr('value') ?? option.text().trim());
  });
  form.find('textarea[name]').each((_, el) => fields.append($(el).attr('name')!, $(el).text()));

  for (const required of ['__VIEWSTATE', '__EVENTVALIDATION', '__VIEWSTATEGENERATOR', DATE_FROM, DATE_TO]) {
    if (!fields.has(required)) throw new VnsatLayoutChangedError(`required form field ${required} not found`);
  }
  if (!form.find('[name]').toArray().some((el) => $(el).attr('name') === SUBMIT)) {
    throw new VnsatLayoutChangedError(`submit control ${SUBMIT} not found`);
  }

  const from = new Date(options.now ?? new Date());
  from.setUTCDate(from.getUTCDate() - 60);
  fields.set(DATE_FROM, formatVnsatDate(from));
  fields.set(DATE_TO, formatVnsatDate(options.now ?? new Date()));
  setSelectByLabel($, fields, COMMODITY, 'Cà phê');
  setSelectByValueOrLabel($, fields, PERIOD, 'ngay');
  fields.set(SUBMIT, 'Xem');

  const cookie = get.headers.get('set-cookie')?.split(/,(?=[^;,]+=)/).map((part) => part.split(';', 1)[0]).join('; ');
  const post = await fetcher(url, {
    method: 'POST',
    headers: {
      'User-Agent': 'AgriContract-PricingService/1.0 (+non-commercial research)',
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
      Referer: url,
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: fields.toString(),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!post.ok) throw new Error(`VNSAT form POST returned HTTP ${post.status}`);
  return parseVnsatTable(await post.text());
}

function setSelectByLabel($: ReturnType<typeof load>, fields: URLSearchParams, name: string, label: string): void {
  const select = $(`select[name="${escapeAttribute(name)}"]`);
  if (!select.length) throw new VnsatLayoutChangedError(`select ${name} not found`);
  const option = select.find('option').filter((_, el) => $(el).text().trim() === label).first();
  if (!option.length) throw new VnsatLayoutChangedError(`option ${label} not found in ${name}`);
  fields.set(name, option.attr('value') ?? option.text().trim());
}

function setSelectByValueOrLabel($: ReturnType<typeof load>, fields: URLSearchParams, name: string, value: string): void {
  const select = $(`select[name="${escapeAttribute(name)}"]`);
  if (!select.length) throw new VnsatLayoutChangedError(`select ${name} not found`);
  const option = select.find('option').filter((_, el) => {
    const item = $(el);
    return item.attr('value') === value || item.text().trim() === value;
  }).first();
  if (!option.length) throw new VnsatLayoutChangedError(`option ${value} not found in ${name}`);
  fields.set(name, option.attr('value') ?? option.text().trim());
}

function formatVnsatDate(date: Date): string {
  return `${String(date.getUTCDate()).padStart(2, '0')}/${String(date.getUTCMonth() + 1).padStart(2, '0')}/${date.getUTCFullYear()}`;
}

function escapeAttribute(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}
