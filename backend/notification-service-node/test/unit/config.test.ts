import { describe, expect, it } from 'vitest';
import { ConfigError, loadConfig } from '../../src/config.js';

const requiredEnv = {
  DB_PASSWORD: 'db-secret',
  SERVICE_INTERNAL_SECRET: 'internal-secret',
};

describe('loadConfig', () => {
  it('applies defaults when optional variables are absent', () => {
    const config = loadConfig({ ...requiredEnv });

    expect(config.serverPort).toBe(8085);
    expect(config.db).toEqual({
      host: 'localhost',
      port: 3311,
      name: 'notification_node_db',
      username: 'root',
      password: 'db-secret',
    });
    expect(config.rabbitmq).toEqual({ host: 'localhost', username: 'guest', password: 'guest' });
    expect(config.mail).toEqual({ host: 'localhost', port: 1025 });
    expect(config.adminEmail).toBe('admin@agricontract.com');
    expect(config.serviceInternalSecret).toBe('internal-secret');
  });

  it('reads every variable name used by the Java service', () => {
    const config = loadConfig({
      ...requiredEnv,
      DB_HOST: 'notification-db',
      DB_PORT: '3306',
      DB_NAME: 'notification_db',
      DB_USERNAME: 'app',
      RABBITMQ_HOST: 'rabbitmq',
      RABBITMQ_USERNAME: 'mq-user',
      RABBITMQ_PASSWORD: 'mq-pass',
      MAIL_HOST: 'mailhog',
      MAIL_PORT: '1026',
      ADMIN_EMAIL: 'ops@example.com',
      SERVER_PORT: '9000',
    });

    expect(config.serverPort).toBe(9000);
    expect(config.db).toEqual({
      host: 'notification-db',
      port: 3306,
      name: 'notification_db',
      username: 'app',
      password: 'db-secret',
    });
    expect(config.rabbitmq).toEqual({ host: 'rabbitmq', username: 'mq-user', password: 'mq-pass' });
    expect(config.mail).toEqual({ host: 'mailhog', port: 1026 });
    expect(config.adminEmail).toBe('ops@example.com');
  });

  it('fails fast and names every missing required variable', () => {
    expect(() => loadConfig({})).toThrow(ConfigError);
    expect(() => loadConfig({})).toThrow('DB_PASSWORD, SERVICE_INTERNAL_SECRET');
  });

  it('treats an empty required variable as missing', () => {
    expect(() => loadConfig({ ...requiredEnv, SERVICE_INTERNAL_SECRET: '' })).toThrow(
      'SERVICE_INTERNAL_SECRET',
    );
  });

  it.each(['abc', '0', '65536', '80.5'])('rejects invalid SERVER_PORT "%s"', (value) => {
    expect(() => loadConfig({ ...requiredEnv, SERVER_PORT: value })).toThrow(ConfigError);
  });
});
