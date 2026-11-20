import { validate } from './env.validation';

describe('validate (environment)', () => {
  it('defaults to the compose file-db and object-storage', () => {
    expect(validate({})).toEqual({
      db: {
        host: 'localhost',
        port: 3317,
        name: 'file_db',
        username: 'root',
        password: 'password',
      },
      storage: {
        endpoint: 'http://localhost:8333',
        region: 'us-east-1',
        bucket: 'agricontract-files',
        accessKeyId: 'agricontract',
        secretAccessKey: 'agricontract-dev-secret',
      },
      serverPort: 8089,
      serviceInternalSecret: 'changeme-internal',
    });
  });

  it('reads every variable', () => {
    const config = validate({
      DB_HOST: 'file-db',
      DB_PORT: '3306',
      S3_ENDPOINT: 'http://object-storage:8333',
      S3_BUCKET: 'files-test',
      S3_ACCESS_KEY: 'k',
      S3_SECRET_KEY: 's',
      S3_REGION: 'eu-west-1',
      SERVER_PORT: '9000',
      SERVICE_INTERNAL_SECRET: 'from-env',
    });
    expect(config.db).toMatchObject({ host: 'file-db', port: 3306 });
    expect(config.storage).toEqual({
      endpoint: 'http://object-storage:8333',
      region: 'eu-west-1',
      bucket: 'files-test',
      accessKeyId: 'k',
      secretAccessKey: 's',
    });
    expect(config.serverPort).toBe(9000);
    expect(config.serviceInternalSecret).toBe('from-env');
  });

  it('reports every invalid value together', () => {
    expect(() =>
      validate({
        DB_PORT: '70000',
        S3_ENDPOINT: 'object-storage:8333',
        S3_BUCKET: 'Files_Bucket',
        S3_SECRET_KEY: ' ',
        SERVICE_INTERNAL_SECRET: '',
      }),
    ).toThrow(
      expect.objectContaining({
        message: expect.stringMatching(
          /^(?=[\s\S]*DB_PORT must be an integer)(?=[\s\S]*S3_ENDPOINT must be an http\(s\) URL)(?=[\s\S]*S3_BUCKET must be)(?=[\s\S]*S3_SECRET_KEY must not be empty)(?=[\s\S]*SERVICE_INTERNAL_SECRET must not be empty)/,
        ) as unknown,
      }) as Error,
    );
  });

  it('never prints the secret key in an error', () => {
    expect(() =>
      validate({ S3_SECRET_KEY: 'top-secret', DB_PORT: 'x' }),
    ).toThrow(
      expect.objectContaining({
        message: expect.not.stringContaining('top-secret') as unknown,
      }) as Error,
    );
  });
});
