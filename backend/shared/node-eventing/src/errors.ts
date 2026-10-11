/**
 * Permanent failure: the message can never be processed as-is (bad JSON, schema drift).
 * Consumers reject it straight to the DLQ without retrying (data-governance §8b, group 2).
 */
export class InvalidEnvelopeError extends Error {
  readonly errors: string[];

  constructor(errors: string[]) {
    super(`Invalid event envelope: ${errors.join('; ')}`);
    this.name = 'InvalidEnvelopeError';
    this.errors = errors;
  }
}

/** Throw from a handler to send the message to the DLQ immediately, without retries. */
export class NonRetryableError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'NonRetryableError';
  }
}

export function isNonRetryable(err: unknown): boolean {
  return err instanceof InvalidEnvelopeError || err instanceof NonRetryableError;
}
