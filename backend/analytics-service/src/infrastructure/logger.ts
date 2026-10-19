import type { Logger } from '@agricontract/eventing';

/** One JSON line per log entry on stdout/stderr; debug is dropped. */
export const jsonLogger: Logger = {
  debug: () => {},
  info: (m, meta) => console.info(JSON.stringify({ level: 'info', msg: m, ...meta })),
  warn: (m, meta) => console.warn(JSON.stringify({ level: 'warn', msg: m, ...meta })),
  error: (m, meta) => console.error(JSON.stringify({ level: 'error', msg: m, ...meta })),
};
