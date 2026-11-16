import { FileTooLargeError, failureCode } from './file-errors';

describe('failureCode', () => {
  it('uses the error code when it looks like one', () => {
    expect(
      failureCode(
        Object.assign(new Error('connect ECONNREFUSED 10.0.0.5:8333'), {
          code: 'ECONNREFUSED',
        }),
      ),
    ).toBe('ECONNREFUSED');
  });

  it('falls back to the class name, never the message', () => {
    const code = failureCode(new FileTooLargeError(10));
    expect(code).toBe('FileTooLargeError');
  });

  it('looks inside an AggregateError (every address of a host refused)', () => {
    const inner = Object.assign(new Error('x'), { code: 'ECONNREFUSED' });
    expect(failureCode(new AggregateError([inner], ''))).toBe('ECONNREFUSED');
  });

  it('looks at the cause of a plain Error', () => {
    const cause = Object.assign(new Error('getaddrinfo'), {
      code: 'ENOTFOUND',
    });
    expect(failureCode(new Error('request failed', { cause }))).toBe(
      'ENOTFOUND',
    );
  });

  it.each([
    ['a code with spaces', { code: 'host 10.0.0.5 refused' }],
    ['a string', 'boom'],
    ['null', null],
  ])('answers UNKNOWN for %s', (_case, err) => {
    expect(failureCode(err)).toBe('UNKNOWN');
  });
});
