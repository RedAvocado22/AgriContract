import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { VnsatLayoutChangedError, parseVnsatTable } from '../src/infrastructure/vnsat/vnsatTable';
import { deepEqual } from './deepEqual';

const fixture = readFileSync(resolve(__dirname, 'fixtures/vnsat-coffee-result.html'), 'utf8');

/** Wraps rows in the same table the VNSAT page uses. */
function page(rows: string): string {
  return `<table id="ctl00_maincontent_GridView1">${rows}</table>`;
}

describe('parseVnsatTable', () => {
  it('reads every price row of the result page, text exactly as shown', () => {
    const rows = parseVnsatTable(fixture);

    assert.equal(rows.length, 4);
    deepEqual(rows[0], {
      itemName: 'Cà phê Robusta nhân xô',
      province: 'Đắk Lắk',
      date: '30-10-2026',
      price: '87.333',
    });
    assert.equal(rows[2]!.province, 'Lâm Đồng');
    assert.equal(rows[3]!.province, 'Quảng Ngãi');
  });

  it('returns no rows when VNSAT has no data for the period', () => {
    const empty = page('<tr><td colspan="4">Không có dữ liệu.</td></tr>');

    deepEqual(parseVnsatTable(empty), []);
  });

  it('fails loudly when the price table is missing', () => {
    assert.throws(() => parseVnsatTable('<html><body>Bảo trì hệ thống</body></html>'), VnsatLayoutChangedError);
  });

  it('fails loudly when a row does not have the 4 expected columns', () => {
    const changed = page('<tr><td>Cà phê</td><td>Đắk Lắk</td><td>30-10-2026</td><td>87.333</td><td>VNĐ</td></tr>');

    assert.throws(() => parseVnsatTable(changed), VnsatLayoutChangedError);
  });
});
