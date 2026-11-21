import type { NestApplicationOptions } from '@nestjs/common';

/**
 * Options of every Nest app built from AppModule (main.ts and the HTTP tests).
 * No global body parser: uploads are streamed straight to object storage,
 * and a parser would read (and buffer) the body first.
 */
export const APP_OPTIONS: NestApplicationOptions = { bodyParser: false };
