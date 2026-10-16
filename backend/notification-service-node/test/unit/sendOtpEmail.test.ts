import { describe, expect, it } from 'vitest';
import { OTP_TIME_BUDGET } from '../../src/application/otpTimeBudget.js';
import { DatabaseDeadlineError, type OperationLogger } from '../../src/application/ports.js';
import { DEADLINE_BEFORE_SEND, SendOtpEmail } from '../../src/application/sendOtpEmail.js';
import { renderOtpSignV1 } from '../../src/infrastructure/email/templates/otpSignV1.js';
import {
  deliveryError,
  FakeEmailSender,
  InMemoryNotificationLogStore,
  ManualClock,
  otpRequest,
} from '../support/otpFakes.js';

const silent: OperationLogger = {
  info: () => undefined,
  warn: () => undefined,
  error: () => undefined,
};

const { requestBudgetMs, dbOperationTimeoutMs, smtpBudgetMs, postSendReserveMs } = OTP_TIME_BUDGET;

function setup(): {
  clock: ManualClock;
  store: InMemoryNotificationLogStore;
  sender: FakeEmailSender;
  useCase: SendOtpEmail;
} {
  const clock = new ManualClock();
  const store = new InMemoryNotificationLogStore();
  const sender = new FakeEmailSender();
  const useCase = new SendOtpEmail({ store, sender, render: renderOtpSignV1, now: clock.now });
  return { clock, store, sender, useCase };
}

function onlyRow(store: InMemoryNotificationLogStore) {
  expect(store.rows.size).toBe(1);
  return [...store.rows.values()][0];
}

describe('SendOtpEmail time budget', () => {
  it('fits the worst-case send path inside the request budget and the caller timeout', () => {
    expect(dbOperationTimeoutMs + smtpBudgetMs + postSendReserveMs).toBeLessThanOrEqual(
      requestBudgetMs,
    );
    expect(postSendReserveMs).toBeGreaterThanOrEqual(dbOperationTimeoutMs);
    expect(requestBudgetMs).toBeLessThan(OTP_TIME_BUDGET.callerTimeoutMs);
  });

  it('caps every database call at the per-call limit and the time left', async () => {
    const { clock, store, useCase } = setup();
    const receivedAt = clock.ms;
    store.beforeCall = (method) => {
      if (method === 'reserve') clock.advance(500);
    };

    expect(await useCase.execute(otpRequest(), silent, receivedAt)).toBe('SENT');

    expect(store.calls.map((call) => call.method)).toEqual(['reserve', 'markSent']);
    for (const call of store.calls) {
      expect(call.timeoutMs).toBeGreaterThan(0);
      expect(call.timeoutMs).toBeLessThanOrEqual(dbOperationTimeoutMs);
    }
  });

  it('gives SMTP its budget but ends it early enough to leave the markSent reserve', async () => {
    const { clock, sender, useCase } = setup();
    const receivedAt = clock.ms;

    await useCase.execute(otpRequest(), silent, receivedAt);

    expect(sender.options[0]?.deadlineAt).toBe(receivedAt + smtpBudgetMs);
    expect(sender.options[0]?.deadlineAt).toBeLessThanOrEqual(
      receivedAt + requestBudgetMs - postSendReserveMs,
    );
  });

  it('still sends when exactly the SMTP budget plus the markSent reserve is left', async () => {
    const { clock, store, sender, useCase } = setup();
    const receivedAt = clock.ms;
    const spent = requestBudgetMs - smtpBudgetMs - postSendReserveMs;
    store.beforeCall = (method) => {
      if (method === 'reserve') clock.advance(spent);
    };

    expect(await useCase.execute(otpRequest(), silent, receivedAt)).toBe('SENT');
    expect(sender.sent).toHaveLength(1);
    expect(sender.options[0]?.deadlineAt).toBe(receivedAt + requestBudgetMs - postSendReserveMs);
  });

  it('does not send when less than that is left, and records FAILED', async () => {
    const { clock, store, sender, useCase } = setup();
    const receivedAt = clock.ms;
    const spent = requestBudgetMs - smtpBudgetMs - postSendReserveMs + 1;
    store.beforeCall = (method) => {
      if (method === 'reserve') clock.advance(spent);
    };

    expect(await useCase.execute(otpRequest(), silent, receivedAt)).toBe('NOT_SENT_DEADLINE');

    expect(sender.sent).toHaveLength(0);
    expect(sender.options).toHaveLength(0);
    expect(onlyRow(store)).toMatchObject({ status: 'FAILED', failureReason: DEADLINE_BEFORE_SEND });
  });

  it('does not send when the request arrives with its budget already spent', async () => {
    const { clock, store, sender, useCase } = setup();

    const outcome = await useCase.execute(otpRequest(), silent, clock.ms - requestBudgetMs);

    expect(outcome).toBe('NOT_SENT_DEADLINE');
    expect(sender.options).toHaveLength(0);
    expect(store.calls[0]).toEqual({ method: 'reserve', timeoutMs: 0 });
  });

  it('does not send when reserve times out (the INSERT may still commit)', async () => {
    const { clock, store, sender, useCase } = setup();
    store.failOn.reserve = new DatabaseDeadlineError('QUERY');

    expect(await useCase.execute(otpRequest(), silent, clock.ms)).toBe('NOT_SENT_DEADLINE');
    expect(sender.options).toHaveLength(0);
  });

  it('does not send when the duplicate-key lookup times out', async () => {
    const { clock, store, sender, useCase } = setup();
    const request = otpRequest();
    store.seed(request, 'PENDING');
    store.failOn.findByKey = new DatabaseDeadlineError('ACQUIRE');

    expect(await useCase.execute(request, silent, clock.ms)).toBe('NOT_SENT_DEADLINE');
    expect(sender.options).toHaveLength(0);
  });

  it('reports a send whose outcome is unknown as DELIVERY_UNKNOWN and never resends it', async () => {
    const { clock, store, sender, useCase } = setup();
    const request = otpRequest();
    sender.failWith = deliveryError('SMTP_DEADLINE_EXCEEDED_AFTER_DATA', 'UNKNOWN');

    expect(await useCase.execute(request, silent, clock.ms)).toBe('DELIVERY_UNKNOWN');
    expect(onlyRow(store)).toMatchObject({
      status: 'FAILED',
      failureReason: 'SMTP_DEADLINE_EXCEEDED_AFTER_DATA',
    });

    sender.failWith = undefined;
    expect(await useCase.execute(request, silent, clock.ms)).toBe('PREVIOUSLY_FAILED');
    expect(sender.sent).toHaveLength(0);
  });

  it('treats an unexpected sender error as unknown, not as not-sent', async () => {
    const { clock, sender, useCase } = setup();
    sender.failWith = new Error('boom');

    expect(await useCase.execute(otpRequest(), silent, clock.ms)).toBe('DELIVERY_UNKNOWN');
  });

  it('answers RESULT_NOT_RECORDED when markSent times out after the provider accepted', async () => {
    const { clock, store, sender, useCase } = setup();
    store.failOn.markSent = new DatabaseDeadlineError('QUERY');

    expect(await useCase.execute(otpRequest(), silent, clock.ms)).toBe('RESULT_NOT_RECORDED');
    expect(sender.sent).toHaveLength(1);
    expect(onlyRow(store)?.status).toBe('PENDING');
  });

  it('gives markSent its own time when SMTP used its whole budget', async () => {
    const { clock, store, sender, useCase } = setup();
    const receivedAt = clock.ms;
    sender.onSend = () => {
      clock.advance(smtpBudgetMs);
    };

    expect(await useCase.execute(otpRequest(), silent, receivedAt)).toBe('SENT');
    const markSent = store.calls.find((call) => call.method === 'markSent');
    expect(markSent?.timeoutMs).toBe(
      Math.min(dbOperationTimeoutMs, requestBudgetMs - smtpBudgetMs),
    );
  });
});
