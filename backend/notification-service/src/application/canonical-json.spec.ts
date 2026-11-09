import { TERMS_SNAPSHOT } from '../../test/support/commands';
import { RFC8785_INPUT, RFC8785_OUTPUT } from '../../test/support/jcs';
import { canonicalJson } from './canonical-json';

describe('canonicalJson (RFC 8785)', () => {
  it('writes the RFC 8785 §3.2.4 sample as the RFC does', () => {
    expect(canonicalJson(JSON.parse(RFC8785_INPUT))).toBe(RFC8785_OUTPUT);
  });

  it('sorts keys by UTF-16 code units, as RFC 8785 §3.2.3 does', () => {
    // The RFC's sorting sample; the emoji sorts before U+FB33 only in UTF-16.
    const sample = {
      '€': 'Euro Sign',
      '\r': 'Carriage Return',
      דּ: 'Hebrew Letter Dalet With Dagesh',
      '1': 'One',
      '😀': 'Emoji: Grinning Face',
      '\u0080': 'Control',
      ö: 'Latin Small Letter O With Diaeresis',
    };

    // Read the order off the text: Object.keys of the parsed result would list
    // the integer-like '1' first whatever the text says.
    const order = [...canonicalJson(sample).matchAll(/:"([^"]+)"/g)].map(
      (m) => m[1],
    );
    expect(order).toEqual([
      'Carriage Return',
      'One',
      'Control',
      'Latin Small Letter O With Diaeresis',
      'Euro Sign',
      'Emoji: Grinning Face',
      'Hebrew Letter Dalet With Dagesh',
    ]);
  });

  it('sorts nested objects and keeps array order and null members', () => {
    expect(
      canonicalJson({ b: [{ z: 1, a: null }, 2], a: { y: true, x: 'v' } }),
    ).toBe('{"a":{"x":"v","y":true},"b":[{"a":null,"z":1},2]}');
  });

  it('writes the terms fixture on one line with no whitespace', () => {
    expect(canonicalJson(TERMS_SNAPSHOT)).toBe(
      '{"agreedPrice":"92000.00","contractTemplateCode":"VN_COFFEE_B2B",' +
        '"contractTemplateVersion":"1.0.0","currency":"VND","milestoneSchedule":' +
        '[{"committedQuantity":1200,"expectedDeliveryDate":"2027-01-15",' +
        '"graceDays":3,"milestoneIndex":1}],"totalContractValue":"110400000.00"}',
    );
  });

  it.each([
    ['NaN', NaN],
    ['Infinity', Infinity],
    ['undefined', undefined],
  ])('refuses %s, which has no JSON form', (_name, value) => {
    expect(() => canonicalJson({ value })).toThrow(TypeError);
  });
});
