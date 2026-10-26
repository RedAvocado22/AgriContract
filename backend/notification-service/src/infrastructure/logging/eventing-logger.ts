import type { Logger } from '@nestjs/common';
import type { Logger as EventingLogger } from '@agricontract/eventing';

/** Routes @agricontract/eventing logs to a Nest logger; eventing's `info` is Nest's `log`. */
export function eventingLogger(logger: Logger): EventingLogger {
  return {
    debug: (message, meta) => logger.debug(format(message, meta)),
    info: (message, meta) => logger.log(format(message, meta)),
    warn: (message, meta) => logger.warn(format(message, meta)),
    error: (message, meta) => logger.error(format(message, meta)),
  };
}

function format(message: string, meta?: Record<string, unknown>): string {
  return meta ? `${message} ${safeStringify(meta)}` : message;
}

/** JSON.stringify that never throws: repeated references print as "[Circular]". */
function safeStringify(value: unknown): string {
  const seen = new WeakSet<object>();
  try {
    return JSON.stringify(value, (_key, v: unknown) => {
      if (typeof v === 'object' && v !== null) {
        if (seen.has(v)) return '[Circular]';
        seen.add(v);
      }
      return typeof v === 'bigint' ? v.toString() : v;
    });
  } catch {
    return '[unserializable meta]';
  }
}
