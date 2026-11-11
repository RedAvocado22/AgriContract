/**
 * Integration test against a real MySQL (compose notification-db), run with
 *   npm run test:int
 * A retry parked at shutdown must not hold a claim: the route, use case,
 * sender and repository are the real ones; only the broker channel and SMTP
 * are fakes.
 */
import { randomUUID } from 'node:crypto';
import { Logger } from '@nestjs/common';
import type { AmqpMessageLike } from '@agricontract/eventing';
import type { Channel } from 'amqplib';
import { createPool, type Pool, type RowDataPacket } from 'mysql2/promise';
import { NotificationSender } from '../../application/notification-sender';
import {
  EmailDeliveryError,
  type EmailPort,
} from '../../application/ports/email.port';
import {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyActivationCancelRequestedUseCase,
  NotifyContractActivationFailedUseCase,
  NotifyContractTerminatedUseCase,
  NotifyUserKycResultUseCase,
  NotifyContractAnchorUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from '../../application/use-cases';
import { validate } from '../../config/env.validation';
import { MysqlNotificationLogRepository } from '../persistence/notification-log.mysql-repository';
import type { AmqpService, ChannelReadyCallback } from './amqp.provider';
import { commandRoutes } from './command-routes';
import { legacyRoutes } from './legacy-routes';
import { NotificationEventConsumer } from './notification-event.consumer';

const db = validate(process.env).db;
type OnMessage = (msg: AmqpMessageLike | null) => void;

describe('shutdown park on real MySQL', () => {
  let pool: Pool;
  const eventIds: string[] = [];

  beforeAll(() => {
    pool = createPool({
      host: db.host,
      port: db.port,
      database: db.name,
      user: db.username,
      password: db.password,
      timezone: 'Z',
    });
  });
  beforeEach(() => {
    for (const level of ['log', 'warn', 'error', 'debug'] as const) {
      jest.spyOn(Logger.prototype, level).mockImplementation(() => undefined);
    }
  });
  afterEach(() => jest.restoreAllMocks());
  afterAll(async () => {
    try {
      if (eventIds.length > 0) {
        await pool.query(
          'DELETE FROM notification_logs WHERE event_id IN (?)',
          [eventIds],
        );
      }
    } finally {
      await pool.end();
    }
  });

  it('SMTP down, shutdown during the retry backoff: the retry is parked, unacked, and no PENDING row is left', async () => {
    const eventId = randomUUID();
    eventIds.push(eventId);
    const sendAttempts: string[] = [];
    const smtpDown: EmailPort = {
      send: (message) => {
        sendAttempts.push(message.to);
        return Promise.reject(new EmailDeliveryError('ECONNREFUSED'));
      },
    };
    const sender = new NotificationSender(
      new MysqlNotificationLogRepository(pool),
      smtpDown,
      { logger: { error: () => undefined } },
    );
    const routes = legacyRoutes({
      escrowLocked: new NotifyEscrowLockedUseCase(sender),
      escrowReleased: new NotifyEscrowReleasedUseCase(sender),
      escrowPenalized: new NotifyEscrowPenalizedUseCase(sender),
      categoryApproved: new NotifyCategoryApprovedUseCase(sender),
      categoryRejected: new NotifyCategoryRejectedUseCase(sender),
    });
    let ready!: ChannelReadyCallback;
    const amqp = {
      onChannelReady: (cb: ChannelReadyCallback) => {
        ready = cb;
        return Promise.resolve();
      },
    } as unknown as AmqpService;
    const consumers = new Map<string, OnMessage>();
    const settled: string[] = [];
    const channel = {
      consume: (queue: string, onMessage: OnMessage) => {
        consumers.set(queue, onMessage);
        return Promise.resolve({ consumerTag: `tag:${queue}` });
      },
      ack: () => void settled.push('ack'),
      nack: () => void settled.push('nack'),
      cancel: () => Promise.resolve({}),
    };
    const commands = commandRoutes({
      contractAnchor: new NotifyContractAnchorUseCase(sender),
      activationCancelRequested: new NotifyActivationCancelRequestedUseCase(
        sender,
      ),
      contractActivationFailed: new NotifyContractActivationFailedUseCase(
        sender,
      ),
      contractTerminated: new NotifyContractTerminatedUseCase(sender),
      userKycResult: new NotifyUserKycResultUseCase(sender),
    });
    const consumer = new NotificationEventConsumer(amqp, routes, commands);
    await consumer.onModuleInit();
    await ready(channel as unknown as Channel);

    consumers.get('notification-svc.escrow.released')?.({
      content: Buffer.from(
        JSON.stringify({
          eventId,
          escrowId: 'e-it',
          buyerEmail: 'buyer@it.test',
          sellerEmail: 'seller@it.test',
        }),
      ),
      fields: { routingKey: 'escrow.released' },
    });
    // Attempt 1: seller claimed, send fails, FAILED recorded, 1s backoff starts.
    for (let i = 0; i < 50 && sendAttempts.length === 0; i++) {
      await new Promise((r) => setTimeout(r, 20));
    }
    await new Promise((r) => setTimeout(r, 200)); // let markFailed land
    const started = Date.now();
    await consumer.beforeApplicationShutdown();
    const drainMs = Date.now() - started;

    expect(sendAttempts).toEqual(['seller@it.test']);
    expect(consumer.parkedCount).toBe(1);
    expect(consumer.inFlightCount).toBe(1);
    expect(settled).toEqual([]); // neither acked nor nacked
    expect(drainMs).toBeLessThan(5_000); // the backoff, not the 40s drain timeout
    const [rows] = await pool.query<RowDataPacket[]>(
      'SELECT user_id, status, retry_count FROM notification_logs WHERE event_id = ?',
      [eventId],
    );
    expect(rows).toEqual([
      { user_id: 'seller@it.test', status: 'FAILED', retry_count: 1 },
    ]);
  }, 20_000);
});
