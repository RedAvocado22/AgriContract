/**
 * Review #157 (rules §4 dependency rule): the use case reports application errors; only the web
 * layer turns them into an HTTP status and wire code.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  ApplicationError,
  IdempotencyConflict,
  IdempotencyKeyRequired,
  ValidationFailed,
} from '../src/application/errors.js';
import { recordManualPrice, type ManualPriceDeps } from '../src/application/usecase/recordManualPrice.js';
import { HttpError, toHttpError } from '../src/infrastructure/http/errors.js';
import { FakeCache, InMemoryPriceHistory, memoryLogger, validBody } from './fakes.js';

function deps(): ManualPriceDeps {
  return {
    repository: new InMemoryPriceHistory(),
    cache: new FakeCache(),
    logger: memoryLogger(),
    now: () => new Date('2026-10-12T05:00:00.000Z'),
  };
}

const admin = { userId: 'admin-1', roles: ['ADMIN'] };

/** Asserts the use case threw `type`, and that it is an application error with no HTTP status. */
function applicationError(type: new (...args: never[]) => ApplicationError) {
  return (err: unknown) => {
    assert.ok(err instanceof type, `expected ${type.name}, got ${String(err)}`);
    assert.ok(!(err instanceof HttpError));
    assert.equal('status' in (err as object), false);
    return true;
  };
}

describe('recordManualPrice throws application errors, not HTTP errors', () => {
  it('missing Idempotency-Key -> IdempotencyKeyRequired', async () => {
    await assert.rejects(recordManualPrice(deps(), admin, undefined, validBody()), applicationError(IdempotencyKeyRequired));
    await assert.rejects(recordManualPrice(deps(), admin, '   ', validBody()), applicationError(IdempotencyKeyRequired));
  });

  it('invalid body or over-long key -> ValidationFailed (with every message)', async () => {
    await assert.rejects(
      recordManualPrice(deps(), admin, 'k', validBody({ price: '00100.00', province: '' })),
      (err: unknown) => {
        applicationError(ValidationFailed)(err);
        assert.equal((err as ValidationFailed).errors.length, 2);
        return true;
      },
    );
    await assert.rejects(recordManualPrice(deps(), admin, 'k'.repeat(256), validBody()), applicationError(ValidationFailed));
  });

  it('same key, different body -> IdempotencyConflict', async () => {
    const d = deps();
    await recordManualPrice(d, admin, 'k', validBody());
    await assert.rejects(recordManualPrice(d, admin, 'k', validBody({ price: '1.00' })), applicationError(IdempotencyConflict));
  });
});

describe('web layer maps application errors (infrastructure/http/errors.ts)', () => {
  const cases: Array<[ApplicationError, number, string]> = [
    [new IdempotencyKeyRequired(), 400, 'IDEMPOTENCY_KEY_REQUIRED'],
    [new ValidationFailed(['price is wrong']), 400, 'VALIDATION_FAILED'],
    [new IdempotencyConflict(), 409, 'IDEMPOTENCY_KEY_STATE_CONFLICT'],
  ];
  for (const [error, status, code] of cases) {
    it(`${error.name} -> ${status} ${code}`, () => {
      const mapped = toHttpError(error);
      assert.equal(mapped?.status, status);
      assert.equal(mapped?.code, code);
      assert.equal(mapped?.message, error.message);
    });
  }

  it('unexpected errors are not mapped (they become 500)', () => {
    assert.equal(toHttpError(new Error('db down')), undefined);
  });
});
