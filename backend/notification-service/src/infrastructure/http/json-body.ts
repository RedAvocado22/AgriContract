import type { Request } from 'express';
import { OtpEmailRequestInvalidError } from '../../application/otp/otp-email-request';

/** Far above a valid OtpEmailRequest (about 400 bytes). */
export const MAX_JSON_BODY_BYTES = 16 * 1024;

/**
 * Reads and parses the JSON body of `req` inside the route, after the guards.
 *
 * The app runs without Nest's body parser (APP_OPTIONS): its errors would
 * reach the client and the logs with JSON.parse's message, which quotes the
 * body, and the body of the OTP endpoint holds an OTP and an email address.
 * Here every failure becomes an OtpEmailRequestInvalidError without any of
 * the body's text. Reading after the guard also means an unauthenticated
 * call never has its body read.
 */
export async function readJsonBody(
  req: Request,
  limit = MAX_JSON_BODY_BYTES,
): Promise<unknown> {
  // Already consumed by a body parser (not the case with APP_OPTIONS).
  if (req.readableEnded) return req.body as unknown;

  const chunks: Buffer[] = [];
  let size = 0;
  let tooLarge = false;
  try {
    for await (const chunk of req as AsyncIterable<Buffer | string>) {
      const buffer = typeof chunk === 'string' ? Buffer.from(chunk) : chunk;
      size += buffer.length;
      if (size > limit) tooLarge = true; // keep draining so the response can be written
      if (!tooLarge) chunks.push(buffer);
    }
  } catch {
    throw new OtpEmailRequestInvalidError(['body could not be read']);
  }
  if (tooLarge) {
    throw new OtpEmailRequestInvalidError([
      `body is larger than ${limit} bytes`,
    ]);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown;
  } catch {
    throw new OtpEmailRequestInvalidError(['body is not valid JSON']);
  }
}
