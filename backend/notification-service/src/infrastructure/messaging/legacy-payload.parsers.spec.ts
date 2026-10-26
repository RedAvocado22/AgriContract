import { NonRetryableError, isNonRetryable } from '@agricontract/eventing';
import {
  parseCategoryApproved,
  parseCategoryRejected,
  parseContractCancelled,
  parseContractDelivered,
  parseContractDisputed,
  parseContractSigned,
  parseEscrowLocked,
  parseEscrowPenalized,
  parseEscrowReleased,
} from './legacy-payload.parsers';

type Parser = (body: Buffer | string) => unknown;

interface Case {
  parse: Parser;
  /** Flat payload as the Java producers publish it, extra fields included. */
  payload: Record<string, unknown>;
  expected: Record<string, unknown>;
  required: string[];
}

const BASE = { eventType: 'x.y', occurredAt: '2026-10-17T00:00:00Z' };

const CASES: Record<string, Case> = {
  'contract.signed': {
    parse: parseContractSigned,
    payload: {
      ...BASE,
      eventId: 'evt-1',
      contractId: 'ctr-1',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      buyerId: 'u-b',
      terms: { quantity: { value: 1000, unit: 'kg' } },
    },
    expected: {
      eventId: 'evt-1',
      contractId: 'ctr-1',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
    required: ['eventId', 'contractId', 'buyerEmail', 'sellerEmail'],
  },
  'contract.cancelled': {
    parse: parseContractCancelled,
    payload: {
      ...BASE,
      eventId: 'evt-2',
      contractId: 'ctr-2',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      reason: 'late',
      cancelledBy: 'BUYER',
      buyerPenaltyRate: 0.05,
    },
    expected: {
      eventId: 'evt-2',
      contractId: 'ctr-2',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      reason: 'late',
    },
    required: ['eventId', 'contractId', 'buyerEmail', 'sellerEmail'],
  },
  'contract.delivered': {
    parse: parseContractDelivered,
    payload: {
      ...BASE,
      eventId: 'evt-3',
      contractId: 'ctr-3',
      sellerEmail: 's@x.test',
      buyerEmail: 'b@x.test',
      confirmedBy: 'u-b',
    },
    expected: {
      eventId: 'evt-3',
      contractId: 'ctr-3',
      sellerEmail: 's@x.test',
    },
    required: ['eventId', 'contractId', 'sellerEmail'],
  },
  'contract.disputed': {
    parse: parseContractDisputed,
    payload: {
      ...BASE,
      eventId: 'evt-4',
      contractId: 'ctr-4',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      reason: 'wet beans',
      disputedBy: 'u-b',
    },
    expected: {
      eventId: 'evt-4',
      contractId: 'ctr-4',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      reason: 'wet beans',
    },
    required: ['eventId', 'contractId', 'buyerEmail', 'sellerEmail'],
  },
  'escrow.locked': {
    parse: parseEscrowLocked,
    payload: {
      ...BASE,
      eventId: 'evt-5',
      escrowId: 'esc-5',
      contractId: 'ctr-5',
      sellerEmail: 's@x.test',
      buyerEmail: 'b@x.test',
    },
    expected: { eventId: 'evt-5', escrowId: 'esc-5', sellerEmail: 's@x.test' },
    required: ['eventId', 'escrowId', 'sellerEmail'],
  },
  'escrow.released': {
    parse: parseEscrowReleased,
    payload: {
      ...BASE,
      eventId: 'evt-6',
      escrowId: 'esc-6',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
    expected: {
      eventId: 'evt-6',
      escrowId: 'esc-6',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
    },
    required: ['eventId', 'escrowId', 'buyerEmail', 'sellerEmail'],
  },
  'escrow.penalized': {
    parse: parseEscrowPenalized,
    payload: {
      ...BASE,
      eventId: 'evt-7',
      escrowId: 'esc-7',
      penalizedParty: 'BUYER',
      buyerEmail: 'b@x.test',
      sellerEmail: 's@x.test',
      penaltyAmount: { amount: 1, currency: 'VND' },
    },
    expected: {
      eventId: 'evt-7',
      escrowId: 'esc-7',
      penalizedPartyEmail: 'b@x.test',
    },
    required: ['eventId', 'escrowId', 'penalizedParty', 'buyerEmail'],
  },
  'category.approved': {
    parse: parseCategoryApproved,
    payload: {
      ...BASE,
      eventId: 'evt-8',
      categoryId: 'cat-8',
      name: 'Cà phê',
      proposedByEmail: 's@x.test',
    },
    expected: {
      eventId: 'evt-8',
      categoryId: 'cat-8',
      name: 'Cà phê',
      proposedByEmail: 's@x.test',
    },
    required: ['eventId', 'name', 'proposedByEmail'],
  },
  'category.rejected': {
    parse: parseCategoryRejected,
    payload: {
      ...BASE,
      eventId: 'evt-9',
      categoryId: 'cat-9',
      name: 'Ca Phe',
      proposedByEmail: 's@x.test',
      rejectionReason: 'Trung ten',
    },
    expected: {
      eventId: 'evt-9',
      categoryId: 'cat-9',
      name: 'Ca Phe',
      proposedByEmail: 's@x.test',
      rejectionReason: 'Trung ten',
    },
    required: ['eventId', 'name', 'proposedByEmail', 'rejectionReason'],
  },
};

const json = (payload: unknown) => Buffer.from(JSON.stringify(payload));

function expectDeadLetter(run: () => unknown, pattern: RegExp) {
  let error: unknown;
  try {
    run();
  } catch (err) {
    error = err;
  }
  expect(error).toBeInstanceOf(NonRetryableError);
  expect(isNonRetryable(error)).toBe(true);
  expect((error as Error).message).toMatch(pattern);
}

describe.each(Object.entries(CASES))('%s parser', (eventType, c) => {
  it('maps a flat payload to the command and ignores extra fields', () => {
    expect(c.parse(json(c.payload))).toEqual(c.expected);
  });

  it('accepts the body as a string too', () => {
    expect(c.parse(JSON.stringify(c.payload))).toEqual(c.expected);
  });

  it('does not trim or convert values', () => {
    const field = c.required[c.required.length - 1];
    const result = c.parse(json({ ...c.payload, [field]: '  x  ' })) as Record<
      string,
      unknown
    >;
    expect(Object.values(result)).toContain('  x  ');
  });

  describe.each(c.required)('required field %s', (field) => {
    const without = () => {
      const copy = { ...c.payload };
      delete copy[field];
      return copy;
    };

    it.each<[string, () => Record<string, unknown>]>([
      ['missing', without],
      ['null', () => ({ ...c.payload, [field]: null })],
      ['empty', () => ({ ...c.payload, [field]: '' })],
      ['a number', () => ({ ...c.payload, [field]: 12345 })],
      ['an object', () => ({ ...c.payload, [field]: { value: 'x' } })],
    ])('dead-letters when %s', (_label, payload) => {
      expectDeadLetter(
        () => c.parse(json(payload())),
        new RegExp(
          `Malformed ${eventType.replace('.', '\\.')} payload: field "${field}"`,
        ),
      );
    });
  });

  it.each([
    ['broken JSON', Buffer.from('{"eventId": "evt-1",')],
    ['an array', json([c.payload])],
    ['null', json(null)],
    ['a number', json(42)],
    ['a string', json('evt-1')],
  ])('dead-letters %s', (_label, body) => {
    expectDeadLetter(() => c.parse(body), /Malformed .* payload: /);
  });
});

describe('optional fields', () => {
  it.each([
    ['contract.cancelled', parseContractCancelled],
    ['contract.disputed', parseContractDisputed],
  ] as const)('%s: a missing or null reason becomes null', (type, parse) => {
    const { reason: _ignored, ...withoutReason } = CASES[type].payload;
    void _ignored;
    expect(parse(json(withoutReason)).reason).toBeNull();
    expect(parse(json({ ...withoutReason, reason: null })).reason).toBeNull();
    expect(parse(json({ ...withoutReason, reason: '' })).reason).toBe('');
  });

  it('a reason of the wrong type is dead-lettered, as Java rejected a wrong type', () => {
    expectDeadLetter(
      () =>
        parseContractCancelled(
          json({ ...CASES['contract.cancelled'].payload, reason: 7 }),
        ),
      /field "reason" must be a string when present/,
    );
  });

  it('categoryId is optional', () => {
    const { categoryId: _ignored, ...payload } =
      CASES['category.approved'].payload;
    void _ignored;
    expect(parseCategoryApproved(json(payload)).categoryId).toBeNull();
  });
});

describe('escrow.penalized recipient (Java: BUYER ? buyerEmail : sellerEmail)', () => {
  const payload = CASES['escrow.penalized'].payload;

  it('BUYER picks the buyer email', () => {
    expect(
      parseEscrowPenalized(json({ ...payload, penalizedParty: 'BUYER' }))
        .penalizedPartyEmail,
    ).toBe('b@x.test');
  });

  it('SELLER picks the seller email', () => {
    expect(
      parseEscrowPenalized(json({ ...payload, penalizedParty: 'SELLER' }))
        .penalizedPartyEmail,
    ).toBe('s@x.test');
  });

  it('any other value picks the seller email, as in Java', () => {
    expect(
      parseEscrowPenalized(json({ ...payload, penalizedParty: 'buyer' }))
        .penalizedPartyEmail,
    ).toBe('s@x.test');
  });

  it('only the chosen party email is required', () => {
    const { sellerEmail: _s, ...buyerOnly } = payload;
    void _s;
    expect(
      parseEscrowPenalized(json({ ...buyerOnly, penalizedParty: 'BUYER' }))
        .penalizedPartyEmail,
    ).toBe('b@x.test');
    expectDeadLetter(
      () =>
        parseEscrowPenalized(json({ ...buyerOnly, penalizedParty: 'SELLER' })),
      /field "sellerEmail" must be a non-empty string/,
    );
  });
});

it('error messages name the field type, never the value', () => {
  expectDeadLetter(
    () =>
      parseContractSigned(
        json({ ...CASES['contract.signed'].payload, buyerEmail: 99 }),
      ),
    /field "buyerEmail" must be a non-empty string, got number \(event evt-1\)$/,
  );
});

describe('error messages never carry payload text (review #172)', () => {
  const PII = ['nguyen.van.a@example.test', 'Nguyễn Văn A', '0912345678'];

  function messageOf(body: string): string {
    let error: unknown;
    try {
      parseContractSigned(body);
    } catch (err) {
      error = err;
    }
    expect(error).toBeInstanceOf(NonRetryableError);
    return (error as Error).message;
  }

  it('broken JSON that contains personal data reports only "not valid JSON"', () => {
    const message = messageOf(
      `{"buyerEmail":"${PII[0]}","buyerName":"${PII[1]}","phone":"${PII[2]}",`,
    );

    expect(message).toBe('Malformed contract.signed payload: not valid JSON');
  });

  it('a missing field names the field and the event id, not other values', () => {
    const message = messageOf(
      JSON.stringify({
        eventId: 'evt-42',
        contractId: 'c',
        buyerEmail: PII[0],
        buyerName: PII[1],
      }),
    );

    expect(message).toBe(
      'Malformed contract.signed payload: field "sellerEmail" must be a non-empty string, got undefined (event evt-42)',
    );
    for (const value of PII) expect(message).not.toContain(value);
  });

  it('an eventId that is not a plain id is left out', () => {
    const message = messageOf(JSON.stringify({ eventId: PII[0] }));

    expect(message).not.toContain(PII[0]);
    expect(message).not.toContain('(event');
  });
});
