import type { NestApplicationOptions } from '@nestjs/common';

/**
 * Options of every Nest app built from AppModule (main.ts and the HTTP tests).
 * No global body parser: the only route with a body (the OTP endpoint) reads
 * it itself, so a malformed body never reaches an error message or a log
 * line (see infrastructure/http/json-body.ts).
 */
export const APP_OPTIONS: NestApplicationOptions = { bodyParser: false };
