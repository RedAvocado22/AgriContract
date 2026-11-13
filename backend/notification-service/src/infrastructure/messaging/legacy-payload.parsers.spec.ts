import { NonRetryableError, isNonRetryable } from '@agricontract/eventing';
import {
  parseCategoryApproved,
  parseCategoryRejected,
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

/*
 * Both parsers read their fields through the one PayloadReader, so required(),
 * optional() and the body checks in read() are the same code for either of
 * them: each of those rules is checked once here, against one field of one
 * parser. What stays per parser below is what actually differs — its field
 * list, the command it builds and its optional field.
 */
describe('the shared PayloadReader rules, on one field of one parser', () => {
  const c = CASES['category.rejected'];
  const FIELD = 'proposedByEmail';
  const without = () => {
    const copy = { ...c.payload };
    delete copy[FIELD];
    return copy;
  };

  it.each<[string, () => Record<string, unknown>]>([
    ['missing', without],
    ['null', () => ({ ...c.payload, [FIELD]: null })],
    ['empty', () => ({ ...c.payload, [FIELD]: '' })],
    ['a number', () => ({ ...c.payload, [FIELD]: 12345 })],
    ['an object', () => ({ ...c.payload, [FIELD]: { value: 'x' } })],
  ])(
    'a required field that is %s dead-letters the message',
    (_label, payload) => {
      expectDeadLetter(
        () => c.parse(json(payload())),
        new RegExp(
          `Malformed category\\.rejected payload: field "${FIELD}" must be a non-empty string`,
        ),
      );
    },
  );

  // Only '' is empty: required() does not trim, so whitespace is a value.
  it('a required field of only whitespace is kept as it is', () => {
    expect(c.parse(json({ ...c.payload, [FIELD]: '   ' }))).toEqual({
      ...c.expected,
      [FIELD]: '   ',
    });
  });

  it.each([
    ['broken JSON', Buffer.from('{"eventId": "evt-1",')],
    ['an array', json([c.payload])],
    ['null', json(null)],
    ['a number', json(42)],
    ['a string', json('evt-1')],
  ])('a body that is %s dead-letters the message', (_label, body) => {
    expectDeadLetter(() => c.parse(body), /Malformed .* payload: /);
  });
});

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

  // One case per required field: that this parser reads the field at all. The
  // five ways a value can be bad are the shared rule tested above.
  it.each(c.required)('dead-letters a payload without %s', (field) => {
    const payload = { ...c.payload };
    delete payload[field];

    expectDeadLetter(
      () => c.parse(json(payload)),
      new RegExp(
        `Malformed ${eventType.replace('.', '\\.')} payload: field "${field}" must be a non-empty string`,
      ),
    );
  });
});

describe('optional fields', () => {
  it('a missing or null categoryId becomes null; an empty one is kept', () => {
    const { categoryId: _ignored, ...payload } =
      CASES['category.approved'].payload;
    void _ignored;
    expect(parseCategoryApproved(json(payload)).categoryId).toBeNull();
    expect(
      parseCategoryApproved(json({ ...payload, categoryId: null })).categoryId,
    ).toBeNull();
    expect(
      parseCategoryApproved(json({ ...payload, categoryId: '' })).categoryId,
    ).toBe('');
  });

  it('a categoryId of the wrong type is dead-lettered, as Java rejected a wrong type', () => {
    expectDeadLetter(
      () =>
        parseCategoryApproved(
          json({ ...CASES['category.approved'].payload, categoryId: 7 }),
        ),
      /field "categoryId" must be a string when present/,
    );
  });
});

it('error messages name the field type, never the value', () => {
  expectDeadLetter(
    () =>
      parseCategoryRejected(
        json({ ...CASES['category.rejected'].payload, proposedByEmail: 99 }),
      ),
    /field "proposedByEmail" must be a non-empty string, got number \(event evt-9\)$/,
  );
});

describe('error messages never carry payload text (review #172)', () => {
  const PII = ['nguyen.van.a@example.test', 'Nguyễn Văn A', '0912345678'];

  function messageOf(body: string): string {
    let error: unknown;
    try {
      parseCategoryRejected(body);
    } catch (err) {
      error = err;
    }
    expect(error).toBeInstanceOf(NonRetryableError);
    return (error as Error).message;
  }

  it('broken JSON that contains personal data reports only "not valid JSON"', () => {
    const message = messageOf(
      `{"proposedByEmail":"${PII[0]}","proposedByName":"${PII[1]}","phone":"${PII[2]}",`,
    );

    expect(message).toBe('Malformed category.rejected payload: not valid JSON');
  });

  it('a missing field names the field and the event id, not other values', () => {
    const message = messageOf(
      JSON.stringify({
        eventId: 'evt-42',
        name: 'Ca Phe',
        proposedByEmail: PII[0],
        proposedByName: PII[1],
      }),
    );

    expect(message).toBe(
      'Malformed category.rejected payload: field "rejectionReason" must be a non-empty string, got undefined (event evt-42)',
    );
    for (const value of PII) expect(message).not.toContain(value);
  });

  it('an eventId that is not a plain id is left out', () => {
    const message = messageOf(JSON.stringify({ eventId: PII[0] }));

    expect(message).not.toContain(PII[0]);
    expect(message).not.toContain('(event');
  });
});
