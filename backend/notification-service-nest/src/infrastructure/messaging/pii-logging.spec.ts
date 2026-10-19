/**
 * Review #172 [BLOCKER]: recipient emails and payload text must never reach
 * the logs. These tests run a delivery end to end (real consumer, eventing
 * handleDelivery with its retries, use case, sender, nodemailer adapter with a
 * fake transport) and capture every line written through the Nest Logger.
 */
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { AmqpMessageLike } from '@agricontract/eventing';
import type { Channel } from 'amqplib';
import { InMemoryNotificationLogRepository } from '../../../test/support/fakes';
import { NotificationSender } from '../../application/notification-sender';
import {
  NotifyContractSignedUseCase,
  NotifyEscrowLockedUseCase,
} from '../../application/use-cases';
import { validate, type AppConfig } from '../../config/env.validation';
import { NodemailerEmailAdapter } from '../email/nodemailer-email.adapter';
import type { AmqpService, ChannelReadyCallback } from './amqp.provider';
import { legacyRoutes, type NotificationUseCases } from './legacy-routes';
import { NotificationEventConsumer } from './notification-event.consumer';

const BUYER = 'buyer.nguyen@example.test';
const SELLER = 'seller.tran@example.test';
const NAME = 'Nguyễn Văn A';

type OnMessage = (msg: AmqpMessageLike | null) => void;

function captureLogs(): string[] {
  const lines: string[] = [];
  for (const level of ['log', 'warn', 'error', 'debug', 'verbose'] as const) {
    jest
      .spyOn(Logger.prototype, level)
      .mockImplementation((...args: unknown[]) => {
        lines.push(args.map((a) => JSON.stringify(a) ?? String(a)).join(' '));
      });
  }
  return lines;
}

/** Wires the real delivery path with a fake broker channel and a fake SMTP transport. */
async function harness(sendMail: jest.Mock) {
  const config = new ConfigService<AppConfig, true>(validate({}));
  const email = new NodemailerEmailAdapter(config, () => ({ sendMail }));
  const sender = new NotificationSender(
    new InMemoryNotificationLogRepository(),
    email,
  );
  const routes = legacyRoutes({
    contractSigned: new NotifyContractSignedUseCase(sender),
    escrowLocked: new NotifyEscrowLockedUseCase(sender),
  } as unknown as NotificationUseCases);

  let ready!: ChannelReadyCallback;
  const amqp = {
    onChannelReady: (cb: ChannelReadyCallback) => {
      ready = cb;
      return Promise.resolve();
    },
  } as unknown as AmqpService;
  const consumers = new Map<string, OnMessage>();
  const nacks: Array<boolean | undefined> = [];
  const channel = {
    consume: (queue: string, onMessage: OnMessage) => {
      consumers.set(queue, onMessage);
      return Promise.resolve({ consumerTag: queue });
    },
    ack: () => undefined,
    nack: (_m: unknown, _all: boolean, requeue: boolean) =>
      void nacks.push(requeue),
    cancel: () => Promise.resolve(),
  };

  const consumer = new NotificationEventConsumer(amqp, routes);
  await consumer.onModuleInit();
  await ready(channel as unknown as Channel);

  const deliver = (queue: string, body: string) =>
    consumers.get(queue)!({ content: Buffer.from(body), fields: {} });
  return { deliver, nacks };
}

describe('no personal data in the logs', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('SMTP rejection quoting the recipient: retries and dead-letter log only eventId and code', async () => {
    const lines = captureLogs();
    const sendMail = jest
      .fn()
      .mockRejectedValue(
        Object.assign(
          new Error(
            `Can't send mail - all recipients were rejected: 550 5.1.1 <${BUYER}>: Recipient address rejected`,
          ),
          { code: 'EENVELOPE', responseCode: 550, rejected: [BUYER] },
        ),
      );
    const { deliver, nacks } = await harness(sendMail);

    deliver(
      'notification-svc.contract.signed',
      JSON.stringify({
        eventId: 'evt-smtp-1',
        contractId: 'ctr-1',
        buyerEmail: BUYER,
        sellerEmail: SELLER,
        buyerName: NAME,
      }),
    );
    await jest.advanceTimersByTimeAsync(5_000); // 3 attempts, 1s + 2s backoff

    expect(sendMail).toHaveBeenCalledTimes(3);
    expect(nacks).toEqual([false]); // dead-lettered
    const log = lines.join('\n');
    expect(log).toContain('evt-smtp-1');
    expect(log).toContain('EENVELOPE/550');
    expect(log).toMatch(/Event dead-lettered/);
    for (const secret of [BUYER, SELLER, NAME, 'Recipient address rejected']) {
      expect(log).not.toContain(secret);
    }
    expect(log).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/); // no email address at all
  });

  it('malformed JSON holding personal data: the dead-letter log shows no payload text', async () => {
    const lines = captureLogs();
    const { deliver, nacks } = await harness(jest.fn());

    deliver(
      'notification-svc.escrow.locked',
      `{"eventId":"evt-json-1","sellerEmail":"${SELLER}","sellerName":"${NAME}",`,
    );
    await jest.advanceTimersByTimeAsync(0);

    expect(nacks).toEqual([false]);
    const log = lines.join('\n');
    expect(log).toContain('Malformed escrow.locked payload: not valid JSON');
    for (const secret of [SELLER, NAME, 'sellerName']) {
      expect(log).not.toContain(secret);
    }
  });

  it('an unexpected error with personal data in its message is reduced to its code', async () => {
    const lines = captureLogs();
    const sendMail = jest.fn().mockResolvedValue({});
    const { deliver } = await harness(sendMail);
    // A bug elsewhere throwing a raw error that quotes an address.
    jest
      .spyOn(NotificationSender.prototype, 'send')
      .mockRejectedValue(
        Object.assign(new Error(`boom for ${BUYER}`), { code: 'EBUG' }),
      );

    deliver(
      'notification-svc.contract.signed',
      JSON.stringify({
        eventId: 'evt-bug-1',
        contractId: 'c',
        buyerEmail: BUYER,
        sellerEmail: SELLER,
      }),
    );
    await jest.advanceTimersByTimeAsync(5_000);

    const log = lines.join('\n');
    expect(log).toContain('Unexpected failure (EBUG)');
    expect(log).not.toContain(BUYER);
  });
});
