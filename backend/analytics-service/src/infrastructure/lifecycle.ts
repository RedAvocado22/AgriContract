import type { Logger } from '@agricontract/eventing';

/** The parts of the running service that shutdown has to stop, in the order they are listed. */
export interface ShutdownSteps {
  markNotReady(): void;
  stopMonitor(): void;
  stopHttp(): Promise<void>;
  stopConsuming(): Promise<void>;
  /** Resolves true when no delivery is being handled, false when the deadline passed first. */
  awaitIdle(timeoutMs: number): Promise<boolean>;
  closeDatabase(): Promise<void>;
  closeBroker(): Promise<void>;
}

export interface ShutdownOptions {
  logger: Logger;
  /** How long in-flight deliveries may take to finish before shutdown continues without them. */
  timeoutMs: number;
  /** Upper bound for each close step, so one stuck resource cannot hold the process forever. */
  stepTimeoutMs?: number;
}

export type ShutdownResult = 'CLEAN' | 'FORCED';

/**
 * Graceful shutdown (rules §4B): not ready -> stop HTTP and new deliveries -> wait for in-flight work
 * (bounded) -> close MySQL, then RabbitMQ. After a timeout, unfinished messages stay unacked and the
 * broker redelivers them; dedupe by eventId makes the repeat safe. Every step runs even if one fails.
 */
export async function gracefulShutdown(steps: ShutdownSteps, options: ShutdownOptions): Promise<ShutdownResult> {
  const { logger } = options;
  const stepTimeout = options.stepTimeoutMs ?? 5000;

  const attempt = async (name: string, work: () => void | Promise<void>): Promise<void> => {
    let timer: NodeJS.Timeout | undefined;
    try {
      await Promise.race([
        Promise.resolve().then(work),
        new Promise<never>((_, reject) => {
          timer = setTimeout(() => reject(new Error(`timed out after ${stepTimeout} ms`)), stepTimeout);
        }),
      ]);
    } catch (err) {
      logger.warn(`Shutdown step failed: ${name}`, { error: err instanceof Error ? err.message : String(err) });
    } finally {
      clearTimeout(timer);
    }
  };

  logger.info('Shutdown started', { timeoutMs: options.timeoutMs });
  await attempt('mark not ready', () => steps.markNotReady());
  await attempt('stop DLQ monitor', () => steps.stopMonitor());
  await attempt('stop consuming', () => steps.stopConsuming());
  await attempt('stop HTTP server', () => steps.stopHttp());

  let drained = false;
  try {
    drained = await steps.awaitIdle(options.timeoutMs);
  } catch (err) {
    logger.warn('Could not wait for in-flight deliveries', { error: err instanceof Error ? err.message : String(err) });
  }
  if (!drained) {
    logger.warn('In-flight deliveries did not finish before the deadline; they will be redelivered and deduplicated', {
      timeoutMs: options.timeoutMs,
    });
  }

  await attempt('close MySQL pool', () => steps.closeDatabase());
  await attempt('close RabbitMQ', () => steps.closeBroker());
  logger.info('Shutdown finished', { result: drained ? 'CLEAN' : 'FORCED' });
  return drained ? 'CLEAN' : 'FORCED';
}
