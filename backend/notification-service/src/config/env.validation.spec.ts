import { validate } from './env.validation';

describe('validate (env)', () => {
  it('uses the Java defaults when nothing is set', () => {
    expect(validate({})).toEqual({
      db: {
        host: 'localhost',
        port: 3311,
        name: 'notification_db',
        username: 'root',
        password: 'password',
      },
      rabbitmq: {
        host: 'localhost',
        port: 5672,
        username: 'guest',
        password: 'guest',
      },
      mail: { host: 'localhost', port: 1025, from: 'noreply@agricontract.com' },
      adminEmail: 'admin@agricontract.com',
      serverPort: 8085,
    });
  });

  it('reads values and converts ports to numbers', () => {
    const config = validate({
      DB_HOST: 'notification-db',
      DB_PORT: '3306',
      RABBITMQ_HOST: 'rabbitmq',
      RABBITMQ_PORT: ' 5673 ',
      MAIL_PORT: '2525',
      SERVER_PORT: '18085',
      ADMIN_EMAIL: 'ops@example.test',
    });

    expect(config.db.host).toBe('notification-db');
    expect(config.db.port).toBe(3306);
    expect(config.rabbitmq).toMatchObject({ host: 'rabbitmq', port: 5673 });
    expect(config.mail.port).toBe(2525);
    expect(config.serverPort).toBe(18085);
    expect(config.adminEmail).toBe('ops@example.test');
  });

  it.each(['abc', '0', '65536', '-1', '80.5', '', ' '])(
    'rejects port value "%s"',
    (value) => {
      expect(() => validate({ DB_PORT: value })).toThrow(/DB_PORT/);
    },
  );

  it('reports every invalid variable at once without echoing secrets', () => {
    let message = '';
    try {
      validate({
        DB_PORT: 'abc',
        SERVER_PORT: '70000',
        DB_HOST: '',
        RABBITMQ_PASSWORD: '  ',
      });
    } catch (err) {
      message = (err as Error).message;
    }

    expect(message).toContain('DB_PORT must be an integer from 1 to 65535');
    expect(message).toContain('SERVER_PORT');
    expect(message).toContain('DB_HOST must not be empty');
    expect(message).toContain('RABBITMQ_PASSWORD must not be empty');
    expect(message.match(/\n {2}- /g)).toHaveLength(4);
  });
});
