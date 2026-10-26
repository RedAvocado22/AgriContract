import { describeError } from './describe-error';

describe('describeError', () => {
  const refused = (address: string) =>
    Object.assign(new Error(`connect ECONNREFUSED ${address}`), {
      code: 'ECONNREFUSED',
    });

  it('lists the inner errors of an AggregateError, whose own message is empty', () => {
    const err = new AggregateError(
      [refused('::1:5672'), refused('127.0.0.1:5672')],
      '',
    );
    expect(describeError(err)).toBe(
      'connect ECONNREFUSED ::1:5672; connect ECONNREFUSED 127.0.0.1:5672',
    );
  });

  it('falls back to the error code, then the name', () => {
    expect(
      describeError(Object.assign(new Error(''), { code: 'ECONNRESET' })),
    ).toBe('ECONNRESET');
    expect(describeError(new TypeError(''))).toBe('TypeError');
  });

  it('stringifies non-errors', () => {
    expect(describeError('boom')).toBe('boom');
  });
});
