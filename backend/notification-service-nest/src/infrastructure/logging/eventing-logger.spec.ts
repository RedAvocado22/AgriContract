import { Logger } from '@nestjs/common';
import { eventingLogger } from './eventing-logger';

describe('eventingLogger', () => {
  function spiedLogger() {
    const logger = new Logger('test');
    return {
      logger,
      log: jest.spyOn(logger, 'log').mockImplementation(() => undefined),
      debug: jest.spyOn(logger, 'debug').mockImplementation(() => undefined),
      warn: jest.spyOn(logger, 'warn').mockImplementation(() => undefined),
      error: jest.spyOn(logger, 'error').mockImplementation(() => undefined),
    };
  }

  it('maps info to log and the other levels one to one', () => {
    const s = spiedLogger();
    const log = eventingLogger(s.logger);

    log.info('i');
    log.debug('d');
    log.warn('w');
    log.error('e');

    expect(s.log).toHaveBeenCalledWith('i');
    expect(s.debug).toHaveBeenCalledWith('d');
    expect(s.warn).toHaveBeenCalledWith('w');
    expect(s.error).toHaveBeenCalledWith('e');
  });

  it('appends meta as JSON', () => {
    const s = spiedLogger();

    eventingLogger(s.logger).warn('retrying', { attempt: 2, queue: 'q' });

    expect(s.warn).toHaveBeenCalledWith('retrying {"attempt":2,"queue":"q"}');
  });

  it('does not throw on circular meta', () => {
    const s = spiedLogger();
    const meta: Record<string, unknown> = { id: 'x' };
    meta.self = meta;

    expect(() => eventingLogger(s.logger).error('boom', meta)).not.toThrow();
    expect(s.error).toHaveBeenCalledWith('boom {"id":"x","self":"[Circular]"}');
  });
});
