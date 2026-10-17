import { EventEmitter } from 'node:events';
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel, ChannelModel } from 'amqplib';
import { validate, type AppConfig } from '../../config/env.validation';
import { AmqpService, PREFETCH, type AmqpConnect } from './amqp.provider';

class FakeChannel extends EventEmitter {
  prefetch = jest.fn(() => Promise.resolve());
  assertExchange = jest.fn(() => Promise.resolve({}));
  assertQueue = jest.fn(() => Promise.resolve({}));
  bindQueue = jest.fn(() => Promise.resolve({}));
  close = jest.fn(() => Promise.resolve());
}

class FakeConnection extends EventEmitter {
  constructor(readonly channel = new FakeChannel()) {
    super();
  }
  createChannel = jest.fn(() => Promise.resolve(this.channel));
  close = jest.fn(() => Promise.resolve());
}

const config = new ConfigService<AppConfig, true>(validate({}));

/** connect() fake: each call takes the next outcome (a connection, or an error to reject with). */
function fakeConnect(...outcomes: Array<FakeConnection | Error>) {
  const queue = [...outcomes];
  return jest.fn<ReturnType<AmqpConnect>, Parameters<AmqpConnect>>(() => {
    const next = queue.shift() ?? new Error('ECONNREFUSED');
    return next instanceof Error
      ? Promise.reject(next)
      : Promise.resolve(next as unknown as ChannelModel);
  });
}

describe('AmqpService', () => {
  let logs: {
    warn: jest.SpyInstance;
    error: jest.SpyInstance;
  };

  beforeEach(() => {
    jest.useFakeTimers();
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'debug').mockImplementation(() => undefined);
    logs = {
      warn: jest
        .spyOn(Logger.prototype, 'warn')
        .mockImplementation(() => undefined),
      error: jest
        .spyOn(Logger.prototype, 'error')
        .mockImplementation(() => undefined),
    };
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('starts without waiting for the broker, then retries with 1s, 2s backoff until connected', async () => {
    const conn = new FakeConnection();
    const connect = fakeConnect(
      new Error('ECONNREFUSED'),
      new Error('ECONNREFUSED'),
      conn,
    );
    const service = new AmqpService(config, connect);

    service.onModuleInit();
    expect(service.isHealthy()).toBe(false);
    await jest.advanceTimersByTimeAsync(0);
    expect(connect).toHaveBeenCalledTimes(1);
    expect(logs.warn).toHaveBeenLastCalledWith(
      expect.stringContaining('retrying in 1000ms'),
    );

    await jest.advanceTimersByTimeAsync(999);
    expect(connect).toHaveBeenCalledTimes(1);
    await jest.advanceTimersByTimeAsync(1);
    expect(connect).toHaveBeenCalledTimes(2);
    expect(logs.warn).toHaveBeenLastCalledWith(
      expect.stringContaining('retrying in 2000ms'),
    );

    await jest.advanceTimersByTimeAsync(2000);
    expect(connect).toHaveBeenCalledTimes(3);
    expect(service.isHealthy()).toBe(true);
    expect(service.getChannel()).toBe(conn.channel);
    expect(connect.mock.calls[2][0]).toMatchObject({
      hostname: 'localhost',
      port: 5672,
      username: 'guest',
      password: 'guest',
    });

    await service.onApplicationShutdown();
  });

  it('sets prefetch, declares the topology and runs callbacks on every (re)connect', async () => {
    const first = new FakeConnection();
    const second = new FakeConnection();
    const service = new AmqpService(config, fakeConnect(first, second));
    const ready = jest.fn<Promise<void>, [Channel]>(() => Promise.resolve());
    await service.onChannelReady(ready); // registered before any channel exists

    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);
    expect(first.channel.prefetch).toHaveBeenCalledWith(PREFETCH);
    expect(first.channel.assertQueue).toHaveBeenCalledTimes(18);
    expect(ready).toHaveBeenCalledWith(first.channel);

    first.channel.emit('error', new Error('PRECONDITION_FAILED'));
    first.channel.emit('close');
    expect(service.isHealthy()).toBe(false);
    expect(() => service.getChannel()).toThrow('not ready');

    await jest.advanceTimersByTimeAsync(1000);
    expect(service.isHealthy()).toBe(true);
    expect(second.channel.prefetch).toHaveBeenCalledWith(PREFETCH);
    expect(second.channel.assertExchange).toHaveBeenCalledTimes(6);
    expect(second.channel.bindQueue).toHaveBeenCalledTimes(18);
    expect(ready).toHaveBeenCalledTimes(2);
    expect(ready).toHaveBeenLastCalledWith(second.channel);
    expect(first.close).toHaveBeenCalled(); // the old connection is not leaked

    await service.onApplicationShutdown();
  });

  it('runs a callback registered after the channel is ready right away', async () => {
    const conn = new FakeConnection();
    const service = new AmqpService(config, fakeConnect(conn));
    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);

    const ready = jest.fn<Promise<void>, [Channel]>(() => Promise.resolve());
    await service.onChannelReady(ready);

    expect(ready).toHaveBeenCalledWith(conn.channel);
    await service.onApplicationShutdown();
  });

  it('logs an error and retries when the topology declaration fails', async () => {
    const broken = new FakeConnection();
    broken.channel.assertQueue.mockRejectedValueOnce(
      new Error('PRECONDITION_FAILED - inequivalent arg'),
    );
    const good = new FakeConnection();
    const service = new AmqpService(config, fakeConnect(broken, good));

    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);
    expect(service.isHealthy()).toBe(false);
    expect(logs.error).toHaveBeenCalledWith(
      expect.stringContaining('PRECONDITION_FAILED'),
    );
    expect(broken.close).toHaveBeenCalled();

    await jest.advanceTimersByTimeAsync(1000);
    expect(service.isHealthy()).toBe(true);
    await service.onApplicationShutdown();
  });

  it('caps the backoff at 30s', async () => {
    const connect = fakeConnect(); // always fails
    const service = new AmqpService(config, connect);

    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);
    // 1+2+4+8+16 s, then 30 s each
    await jest.advanceTimersByTimeAsync(31_000);
    expect(logs.warn).toHaveBeenLastCalledWith(
      expect.stringContaining('retrying in 30000ms'),
    );
    const calls = connect.mock.calls.length;
    await jest.advanceTimersByTimeAsync(29_999);
    expect(connect).toHaveBeenCalledTimes(calls);
    await jest.advanceTimersByTimeAsync(1);
    expect(connect).toHaveBeenCalledTimes(calls + 1);

    await service.onApplicationShutdown();
  });

  it('stops retrying once destroyed', async () => {
    const connect = fakeConnect();
    const service = new AmqpService(config, connect);

    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);
    service.onModuleDestroy();
    await jest.advanceTimersByTimeAsync(120_000);

    expect(connect).toHaveBeenCalledTimes(1);
  });

  it('keeps the channel open on module destroy so consumers can drain', async () => {
    const conn = new FakeConnection();
    const service = new AmqpService(config, fakeConnect(conn));
    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);

    service.onModuleDestroy();

    expect(service.isHealthy()).toBe(true);
    expect(service.getChannel()).toBe(conn.channel);
    expect(conn.channel.close).not.toHaveBeenCalled();
    await service.onApplicationShutdown();
  });

  it('closes channel then connection on application shutdown and swallows "already closed"', async () => {
    const conn = new FakeConnection();
    conn.channel.close.mockRejectedValueOnce(new Error('Channel closed'));
    const service = new AmqpService(config, fakeConnect(conn));
    service.onModuleInit();
    await jest.advanceTimersByTimeAsync(0);

    service.onModuleDestroy();
    await expect(service.onApplicationShutdown()).resolves.toBeUndefined();

    expect(conn.channel.close).toHaveBeenCalledTimes(1);
    expect(conn.close).toHaveBeenCalledTimes(1);
    expect(service.isHealthy()).toBe(false);
    conn.emit('close'); // late close event after shutdown: no reconnect
    await jest.advanceTimersByTimeAsync(60_000);
    expect(conn.createChannel).toHaveBeenCalledTimes(1);
  });
});
