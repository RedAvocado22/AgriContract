import { Logger, type INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import type { App } from 'supertest/types';
import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import { otpKey, otpRequest } from '../../../test/support/otp';
import { APP_OPTIONS } from '../../app.options';
import { SendOtpEmailUseCase } from '../../application/otp/send-otp-email.use-case';
import { validate } from '../../config/env.validation';
import { NotificationStatus } from '../../domain/notification-log';
import type { ErrorEnvelope } from './error-envelope.filter';
import { InternalSecretGuard } from './internal-secret.guard';
import { MAX_JSON_BODY_BYTES } from './json-body';
import { OtpEmailController } from './otp-email.controller';

const PATH = '/internal/v1/notifications/otp-email';
const SECRET = 'test-internal-secret';

describe('POST /internal/v1/notifications/otp-email', () => {
  let app: INestApplication;
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let logged: string[];
  const body = otpRequest();

  beforeEach(async () => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    logged = [];
    for (const level of ['log', 'warn', 'error', 'debug'] as const) {
      jest
        .spyOn(Logger.prototype, level)
        .mockImplementation((...args: unknown[]) => {
          logged.push(args.map(String).join(' '));
        });
    }
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          validate: () => validate({ SERVICE_INTERNAL_SECRET: SECRET }),
        }),
      ],
      controllers: [OtpEmailController],
      providers: [
        InternalSecretGuard,
        {
          provide: SendOtpEmailUseCase,
          useFactory: () =>
            new SendOtpEmailUseCase(logs, email, new Logger('test')),
        },
      ],
    }).compile();
    app = moduleRef.createNestApplication(APP_OPTIONS);
    await app.init();
  });

  afterEach(async () => {
    await app.close();
    jest.restoreAllMocks();
  });

  const post = () =>
    request(app.getHttpServer() as App)
      .post(PATH)
      .set('X-Internal-Secret', SECRET);

  const expectEnvelope = (
    res: request.Response,
    status: number,
    code: string,
  ) => {
    expect(res.status).toBe(status);
    const envelope = res.body as ErrorEnvelope;
    expect(Object.keys(envelope).sort()).toEqual(
      [
        'code',
        'error',
        'message',
        'path',
        'status',
        'timestamp',
        'traceId',
      ].sort(),
    );
    expect(envelope).toMatchObject({ status, code, path: PATH });
    expect(Date.parse(envelope.timestamp)).not.toBeNaN();
    return envelope;
  };

  const expectNoLeak = (res: request.Response) => {
    const outputs = [res.text, ...logged].join('\n');
    expect(outputs).not.toContain(body.otpCode);
    expect(outputs).not.toContain(body.recipientEmail);
  };

  it('returns 200 with an empty body once the provider accepted the email', async () => {
    const res = await post().send(body);

    expect(res.status).toBe(200);
    expect(res.text).toBe('');
    expect(email.sent).toHaveLength(1);
    expect(email.sent[0].body).toContain(body.otpCode);
    expect(logs.get(otpKey(body))?.status).toBe(NotificationStatus.SENT);
    expectNoLeak(res);
  });

  it('returns 200 again for the same requestId without sending a second email', async () => {
    await post().send(body).expect(200);

    await post().send(body).expect(200);
    expect(email.sent).toHaveLength(1);
  });

  it.each([
    ['no secret', undefined],
    ['a wrong secret', 'wrong'],
    ['the secret plus another value', `${SECRET}x`],
  ])('returns 401 with %s and sends nothing', async (_case, secret) => {
    const req = request(app.getHttpServer() as App).post(PATH);
    if (secret !== undefined) req.set('X-Internal-Secret', secret);

    const res = await req.send(body);

    const envelope = expectEnvelope(res, 401, 'UNAUTHORIZED');
    expect(envelope.message).toBe('Unauthorized');
    expect(email.sent).toHaveLength(0);
  });

  it('does not accept the gateway secret header', async () => {
    const res = await request(app.getHttpServer() as App)
      .post(PATH)
      .set('X-Gateway-Secret', SECRET)
      .send(body);

    expectEnvelope(res, 401, 'UNAUTHORIZED');
  });

  it('returns 400 for malformed JSON without quoting the body', async () => {
    const res = await post()
      .set('Content-Type', 'application/json')
      .send(
        `{"otpCode":"${body.otpCode}","recipientEmail":"${body.recipientEmail}"`,
      );

    const envelope = expectEnvelope(res, 400, 'VALIDATION_ERROR');
    expect(envelope.message).toBe('body is not valid JSON');
    expectNoLeak(res);
  });

  it('returns 400 naming the invalid fields only', async () => {
    const res = await post().send({ ...body, otpCode: '12', extra: true });

    const envelope = expectEnvelope(res, 400, 'VALIDATION_ERROR');
    expect(envelope.message).toBe('1 unexpected field(s); otpCode is invalid');
    expect(email.sent).toHaveLength(0);
  });

  it('returns 400 for a body over the size limit', async () => {
    const res = await post()
      .set('Content-Type', 'application/json')
      .send(
        JSON.stringify({
          ...body,
          contractId: 'x'.repeat(MAX_JSON_BODY_BYTES),
        }),
      );

    const envelope = expectEnvelope(res, 400, 'VALIDATION_ERROR');
    expect(envelope.message).toBe(
      `body is larger than ${MAX_JSON_BODY_BYTES} bytes`,
    );
  });

  it('returns 502 when the provider rejects, logging the requestId and code only', async () => {
    email.failFor.add(body.recipientEmail);

    const res = await post().send(body);

    expectEnvelope(res, 502, 'OTP_EMAIL_PROVIDER_FAILED');
    expect(logged.join('\n')).toContain(
      `OTP request ${body.requestId}: email delivery failed (EENVELOPE/550)`,
    );
    expectNoLeak(res);
  });

  it('returns 503 at once while another call sends the same requestId', async () => {
    logs.seed(otpKey(body), NotificationStatus.PENDING);

    const res = await post().send(body);

    expectEnvelope(res, 503, 'OTP_EMAIL_IN_PROGRESS');
    expect(email.sent).toHaveLength(0);
  });

  it('returns 404 as an envelope for an unknown internal route', async () => {
    const res = await request(app.getHttpServer() as App)
      .post('/internal/v1/notifications/other')
      .set('X-Internal-Secret', SECRET)
      .send(body);

    expect(res.status).toBe(404);
    expectNoLeak(res);
  });
});
