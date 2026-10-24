/**
 * Application failures. They carry no HTTP status or wire code: mapping an error to HTTP happens
 * only in the web layer (infrastructure/http/errors.ts), per the dependency rule.
 */
export abstract class ApplicationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

export class IdempotencyKeyRequired extends ApplicationError {
  constructor() {
    super('Idempotency-Key header is required');
  }
}

export class ValidationFailed extends ApplicationError {
  constructor(readonly errors: string[]) {
    super(errors.join('; '));
  }
}

/** The Idempotency-Key was already used by this actor with a different request body. */
export class IdempotencyConflict extends ApplicationError {
  constructor() {
    super('This Idempotency-Key was already used with a different request body');
  }
}
