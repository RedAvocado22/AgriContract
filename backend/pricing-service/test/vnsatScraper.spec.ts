import assert from 'node:assert/strict';
import { scrapeVnsatCoffee, type VnsatFetch } from '../src/infrastructure/vnsat/vnsatScraper';

const landingPage = `<!doctype html><form id="aspnetForm" method="post">
  <input type="hidden" name="__VIEWSTATE" value="view-state">
  <input type="hidden" name="__EVENTVALIDATION" value="event-validation">
  <input type="hidden" name="__VIEWSTATEGENERATOR" value="view-generator">
  <input type="hidden" name="ctl00$maincontent$tu_ngay" value="01/11/2026">
  <input type="hidden" name="ctl00$maincontent$den_ngay" value="12/11/2026">
  <input name="ctl00$maincontent$other" value="preserve-me">
  <select name="ctl00$maincontent$Ngành_hàng"><option value="other">Khác</option><option value="coffee">Cà phê</option></select>
  <select name="ctl00$maincontent$Theo_thời_gian"><option value="month">Tháng</option><option value="ngay">Ngày</option></select>
  <input type="submit" name="ctl00$maincontent$Xem" value="Xem">
</form>`;

const resultsPage = `<!doctype html><table id="ctl00_maincontent_GridView1"><tr><th>H</th></tr>
  <tr><td>Cà phê Robusta nhân xô</td><td>Đắk Lắk</td><td>10-11-2026</td><td>88.500</td></tr>
</table>`;

describe('scrapeVnsatCoffee', () => {
  it('carries cookies and the full WebForms state in a coffee/daily query', async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher: VnsatFetch = async (url, init) => {
      calls.push({ url, init });
      const body = calls.length === 1 ? landingPage : resultsPage;
      return {
        ok: true,
        status: 200,
        headers: { get: (name) => name.toLowerCase() === 'set-cookie' && calls.length === 1 ? 'ASP.NET_SessionId=abc; Path=/; HttpOnly' : null },
        text: async () => body,
      };
    };

    const rows = await scrapeVnsatCoffee({
      url: 'https://vnsat.example/nguonwmy.aspx',
      now: new Date('2026-11-12T00:00:00.000Z'),
      fetcher,
    });

    assert.equal(calls.length, 2);
    assert.equal(calls[0]?.init?.method, 'GET');
    assert.equal(calls[1]?.init?.method, 'POST');
    assert.equal(calls[1]?.init?.headers && (calls[1]!.init!.headers as Record<string, string>).Cookie, 'ASP.NET_SessionId=abc');
    const form = new URLSearchParams(String(calls[1]?.init?.body));
    assert.equal(form.get('__VIEWSTATE'), 'view-state');
    assert.equal(form.get('__EVENTVALIDATION'), 'event-validation');
    assert.equal(form.get('__VIEWSTATEGENERATOR'), 'view-generator');
    assert.equal(form.get('ctl00$maincontent$other'), 'preserve-me');
    assert.equal(form.get('ctl00$maincontent$Ngành_hàng'), 'coffee');
    assert.equal(form.get('ctl00$maincontent$Theo_thời_gian'), 'ngay');
    assert.equal(form.get('ctl00$maincontent$Xem'), 'Xem');
    assert.deepEqual(rows, [{ itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', date: '10-11-2026', price: '88.500' }]);
  });

  it('fails loudly for non-200 source responses', async () => {
    const fetcher: VnsatFetch = async () => ({ ok: false, status: 503, headers: { get: () => null }, text: async () => '' });
    await assert.rejects(scrapeVnsatCoffee({ fetcher }), /HTTP 503/);
  });
});
