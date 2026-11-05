import assert from 'node:assert/strict';
import { isoDate, toVnsatPrice, vndPerKg } from '../src/infrastructure/vnsat/vnsatPrice';
import { VnsatLayoutChangedError } from '../src/infrastructure/vnsat/vnsatTable';
import { deepEqual } from './deepEqual';

describe('toVnsatPrice', () => {
  it('converts a VNSAT row to the formats price_history uses', () => {
    const row = { itemName: 'Cà phê Robusta nhân xô', province: 'Đắk Lắk', date: '30-10-2026', price: '87.333' };

    deepEqual(toVnsatPrice(row), {
      itemName: 'Cà phê Robusta nhân xô',
      province: 'Đắk Lắk',
      price: '87333.00',
      capturedAt: '2026-10-30',
    });
  });
});

describe('vndPerKg', () => {
  it('reads "." as the Vietnamese thousands separator', () => {
    assert.equal(vndPerKg('87.333'), '87333.00');
    assert.equal(vndPerKg('1.087.333'), '1087333.00');
    assert.equal(vndPerKg('950'), '950.00');
  });

  it('rejects anything that is not a whole VND amount written like 87.333', () => {
    for (const bad of ['', 'abc', '87,333', '87.33', '87.3333', '087.333', '0', '-87.333', '87.333 đ']) {
      assert.throws(() => vndPerKg(bad), VnsatLayoutChangedError, bad);
    }
  });

  it('rejects a price above the 99.999.999 VND/kg the column can hold', () => {
    assert.equal(vndPerKg('99.999.999'), '99999999.00');
    assert.throws(() => vndPerKg('100.000.000'), VnsatLayoutChangedError);
  });
});

describe('isoDate', () => {
  it('turns dd-mm-yyyy into yyyy-mm-dd', () => {
    assert.equal(isoDate('30-10-2026'), '2026-10-30');
    assert.equal(isoDate('29-02-2028'), '2028-02-29');
  });

  it('rejects other formats and dates that do not exist', () => {
    for (const bad of ['2026-10-30', '30/10/2026', '3-10-2026', '31-02-2026', '29-02-2026', '00-10-2026', '30-13-2026']) {
      assert.throws(() => isoDate(bad), VnsatLayoutChangedError, bad);
    }
  });
});
