import { ConfigService } from '@nestjs/config';
import { validate, type AppConfig } from '../../config/env.validation';
import { EmailDeliveryError } from '../../application/ports/email.port';
import {
  NodemailerEmailAdapter,
  smtpFailureCode,
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

  describe('errors carry a code only, never an address (review #172)', () => {
    async function rejectionOf(error: unknown): Promise<unknown> {
      const { adapter } = adapterWith(jest.fn().mockRejectedValue(error));
      try {
        await adapter.send({
          to: 'buyer.nguyen@example.test',
          subject: 's',
          body: 'b',
        });
      } catch (err) {
        return err;
      }
      throw new Error('send() did not reject');
    }

    it('turns an SMTP rejection into EmailDeliveryError(EENVELOPE/550)', async () => {
      const smtp = Object.assign(
        new Error(
          "Can't send mail - all recipients were rejected: 550 5.1.1 <buyer.nguyen@example.test>: Recipient address rejected",
        ),
        {
          code: 'EENVELOPE',
          responseCode: 550,
          rejected: ['buyer.nguyen@example.test'],
        },
      );

      const err = await rejectionOf(smtp);

      expect(err).toBeInstanceOf(EmailDeliveryError);
      expect((err as EmailDeliveryError).code).toBe('EENVELOPE/550');
      expect((err as Error).message).toBe(
        'email delivery failed (EENVELOPE/550)',
      );
      expect((err as Error).cause).toBeUndefined();
      expect(JSON.stringify(err)).not.toContain('@');
    });

    it('uses the first inner code of an AggregateError (localhost on ::1 and 127.0.0.1)', async () => {
      const refused = (address: string) =>
        Object.assign(new Error(`connect ECONNREFUSED ${address}`), {
          code: 'ECONNREFUSED',
        });

      const err = await rejectionOf(
        new AggregateError(
          [refused('::1:1025'), refused('127.0.0.1:1025')],
          '',
        ),
      );

      expect((err as Error).message).toBe(
        'email delivery failed (ECONNREFUSED)',
      );
    });

    it('falls back to the error class name when there is no code', async () => {
      const err = await rejectionOf(
        new TypeError('<buyer.nguyen@example.test>'),
      );

      expect((err as Error).message).toBe('email delivery failed (TypeError)');
    });
  });
});

describe('smtpFailureCode', () => {
  it('ignores codes that do not look like codes', () => {
    expect(
      smtpFailureCode(Object.assign(new Error('x'), { code: 'a b <c@d>' })),
    ).toBe('Error');
    expect(smtpFailureCode('oops')).toBe('UNKNOWN');
  });
});
