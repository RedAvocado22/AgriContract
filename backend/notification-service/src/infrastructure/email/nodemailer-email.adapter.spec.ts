import { ConfigService } from '@nestjs/config';
import { validate, type AppConfig } from '../../config/env.validation';
import { EmailDeliveryError } from '../../application/ports/email.port';
import {
  NodemailerEmailAdapter,
  OTP_SMTP_TIMEOUTS,
  sentMessageId,
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

  it('uses the given timeouts (OTP: well below the caller read timeout of 5s)', () => {
    const create = jest.fn(() => ({
      sendMail: jest.fn(),
    })) as unknown as jest.Mock & CreateMailTransport;
    const config = new ConfigService<AppConfig, true>(validate({}));

    new NodemailerEmailAdapter(config, create, OTP_SMTP_TIMEOUTS);

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({
        connectionTimeout: 1_000,
        greetingTimeout: 1_000,
        socketTimeout: 2_500,
      }),
    );
    const { connectionTimeout, greetingTimeout, socketTimeout } =
      OTP_SMTP_TIMEOUTS;
    expect(connectionTimeout + greetingTimeout + socketTimeout).toBeLessThan(
      5_000,
    );
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

  /*
   * The log keeps the provider's id with the sent time (design §8), so the
   * adapter has to report what the server answered. It is evidence only: an
   * email the server accepted is sent whether or not it named an id, so a
   * missing one must never turn into a failure (which would send a second
   * copy on the retry).
   */
  describe('provider message id', () => {
    it('reports the Message-ID the server accepted', async () => {
      const sendMail = jest
        .fn()
        .mockResolvedValue({ messageId: '<abc123@agricontract.test>' });
      const { adapter } = adapterWith(sendMail);

      await expect(
        adapter.send({ to: 'a@x.test', subject: 's', body: 'b' }),
      ).resolves.toEqual({ providerMessageId: '<abc123@agricontract.test>' });
    });

    it.each([
      ['no id at all', {}],
      ['a blank id', { messageId: '   ' }],
      ['nothing but the id missing', { response: '250 OK' }],
    ])('succeeds with a null id when the server gives %s', async (_c, info) => {
      const { adapter } = adapterWith(jest.fn().mockResolvedValue(info));

      await expect(
        adapter.send({ to: 'a@x.test', subject: 's', body: 'b' }),
      ).resolves.toEqual({ providerMessageId: null });
    });

    it('survives a transport that resolves with nothing', async () => {
      const { adapter } = adapterWith(jest.fn().mockResolvedValue(undefined));

      await expect(
        adapter.send({ to: 'a@x.test', subject: 's', body: 'b' }),
      ).resolves.toEqual({ providerMessageId: null });
    });

    it('trims the id, as a header value may arrive padded', () => {
      expect(sentMessageId({ messageId: ' <abc@x.test> ' })).toBe(
        '<abc@x.test>',
      );
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
