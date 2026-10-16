import type { Logger } from '@nestjs/common';
import type { Logger as EventingLogger } from '@agricontract/eventing';

/** Routes @agricontract/eventing logs to a Nest logger; eventing's `info` is Nest's `log`. */
export function eventingLogger(logger: Logger): EventingLogger {
  const format = (message: string, meta?: Record<string, unknown>) =>
    meta ? `${message} ${JSON.stringify(meta)}` : message;
  return {
    debug: (message, meta) => logger.debug(format(message, meta)),
    info: (message, meta) => logger.log(format(message, meta)),
    warn: (message, meta) => logger.warn(format(message, meta)),
    error: (message, meta) => logger.error(format(message, meta)),
  };
}
