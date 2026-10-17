import { ConfigService } from '@nestjs/config';
import { validate, type AppConfig } from '../../config/env.validation';
import {
  NodemailerEmailAdapter,
  type CreateMailTransport,
} from './nodemailer-email.adapter';

function adapterWith(sendMail: jest.Mock, env: Record<string, string> = {}) {
  const create = jest.fn(() => ({ sendMail })) as unknown as jest.Mock &
    CreateMailTransport;
  const config = new ConfigService<AppConfig, true>(validate(env));
  return { adapter: new NodemailerEmailAdapter(config, create), create };
}

describe('NodemailerEmailAdapter', () => {
  it('uses plain SMTP without auth or STARTTLS, like the Java config', () => {
    const { create } = adapterWith(jest.fn(), {
      MAIL_HOST: 'mailhog',
      MAIL_PORT: '1025',
    });

    expect(create).toHaveBeenCalledWith({
      host: 'mailhog',
      port: 1025,
      secure: false,
      ignoreTLS: true,
      auth: undefined,
      connectionTimeout: 10_000,
      greetingTimeout: 10_000,
      socketTimeout: 30_000,
    });
  });

  it('sends a plain text mail from MAIL_FROM', async () => {
    const sendMail = jest.fn().mockResolvedValue({ messageId: 'm-1' });
    const { adapter } = adapterWith(sendMail, {
      MAIL_FROM: 'noreply@example.test',
    });

    await adapter.send({
      to: 'buyer@example.test',
      subject: '[AgriContract] Escrow locked — proceed with delivery',
      body: 'Escrow esc-1 has been successfully locked.',
    });

    expect(sendMail).toHaveBeenCalledWith({
      from: 'noreply@example.test',
      to: 'buyer@example.test',
      subject: '[AgriContract] Escrow locked — proceed with delivery',
      text: 'Escrow esc-1 has been successfully locked.',
    });
    expect(
      (sendMail.mock.calls[0] as [Record<string, unknown>])[0],
    ).not.toHaveProperty('html');
  });

  it('defaults from to noreply@agricontract.com', async () => {
    const sendMail = jest.fn().mockResolvedValue({});
    const { adapter } = adapterWith(sendMail);

    await adapter.send({ to: 'a@x.test', subject: 's', body: 'b' });

    expect(
      (sendMail.mock.calls[0] as [Record<string, unknown>])[0],
    ).toMatchObject({
      from: 'noreply@agricontract.com',
    });
  });

  it('propagates send errors so the log is marked FAILED', async () => {
    const { adapter } = adapterWith(
      jest
        .fn()
        .mockRejectedValue(new Error('connect ECONNREFUSED 127.0.0.1:1025')),
    );

    await expect(
      adapter.send({ to: 'a@x.test', subject: 's', body: 'b' }),
    ).rejects.toThrow(
      'SMTP localhost:1025: connect ECONNREFUSED 127.0.0.1:1025',
    );
  });

  it('names the inner errors of an AggregateError, whose own message is empty', async () => {
    const refused = (address: string) =>
      Object.assign(new Error(`connect ECONNREFUSED ${address}`), {
        code: 'ECONNREFUSED',
      });
    const { adapter } = adapterWith(
      jest
        .fn()
        .mockRejectedValue(
          new AggregateError(
            [refused('::1:1025'), refused('127.0.0.1:1025')],
            '',
          ),
        ),
    );

    await expect(
      adapter.send({ to: 'a@x.test', subject: 's', body: 'b' }),
    ).rejects.toThrow(
      'SMTP localhost:1025: connect ECONNREFUSED ::1:1025; connect ECONNREFUSED 127.0.0.1:1025',
    );
  });
});
