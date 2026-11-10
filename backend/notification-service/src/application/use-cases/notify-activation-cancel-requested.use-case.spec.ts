import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  CANCEL_RECIPIENT,
  cancelCommand,
} from '../../../test/support/commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyActivationCancelRequestedUseCase,
  activationCancelBody,
} from './notify-activation-cancel-requested.use-case';

const KIND = COMMAND_KINDS.activationCancelRequested;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyActivationCancelRequestedUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
  );
  return { logs, email, useCase };
}

const keyOf = (eventId: string, recipientEmail: string) => ({
  eventId,
  recipientEmail,
  notificationType: KIND.notificationType,
});

describe('NotifyActivationCancelRequestedUseCase', () => {
  it('emails the other party, with the contract in the subject', async () => {
    const { email, useCase } = setup();
    const command = cancelCommand();

    await useCase.execute(command);

    expect(email.sent.map((m) => m.to)).toEqual(['seller@cancel.test']);
    expect(email.sent[0].subject).toBe(
      `[AgriContract] Cancel requested for contract ${command.contractId}`,
    );
  });

  it('records the row with its type, template version and platform id', async () => {
    const { logs, useCase } = setup();
    const command = cancelCommand();

    await useCase.execute(command);

    expect(
      logs.get(keyOf(command.eventId, CANCEL_RECIPIENT.email)),
    ).toMatchObject({
      status: NotificationStatus.SENT,
      eventType: 'notification.activation_cancel_requested',
      notificationType: 'ACTIVATION_CANCEL_REQUESTED',
      templateVersion: 'activation-cancel-requested/v1',
      recipientUserId: CANCEL_RECIPIENT.userId,
    });
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(cancelCommand());
    await useCase.execute(cancelCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(CANCEL_RECIPIENT.email);

    await expect(useCase.execute(cancelCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(cancelCommand());
    expect(email.sent).toHaveLength(1);
  });
});

describe('activationCancelBody (activation-cancel-requested/v1)', () => {
  const command = cancelCommand();

  it('tells the other party who asked, what they can do, and from when silence is enough', () => {
    expect(activationCancelBody(command, CANCEL_RECIPIENT)).toBe(
      [
        'The buyer has asked to cancel contract',
        `${command.contractId}.`,
        '',
        'The contract was signed but never became active, because the deposits',
        'could not be locked. Nobody breached it, so nobody is penalised.',
        '',
        'If you also want to cancel, request the cancel in AgriContract: once',
        'both parties have asked, the refund of the deposits starts at once.',
        '',
        "If you do nothing, the buyer's request alone is enough from:",
        '2026-11-14 10:00:00 Vietnam time (2026-11-14T03:00:00.000Z UTC)',
        'Unless the buyer withdraws the request by then, the contract is',
        'then cancelled and the deposits returned without anything more from you.',
        '',
        'If the deposits are locked before then, the contract becomes active as',
        'signed and every cancel request lapses.',
        '',
      ].join('\n'),
    );
  });

  it('names the seller when the seller asked', () => {
    const body = activationCancelBody(
      { ...command, requestedByRole: 'SELLER' },
      { ...CANCEL_RECIPIENT, role: 'BUYER' },
    );

    expect(body).toMatch(/^The seller has asked to cancel contract/);
    expect(body).toContain("the seller's request alone is enough from:");
    expect(body).toContain('If you also want to cancel');
  });

  it.each([
    ['an OPERATOR', 'OPERATOR'],
    ['the requesting party itself', 'BUYER'],
  ] as const)('gives %s the facts without "you can" lines', (_name, role) => {
    const body = activationCancelBody(command, {
      ...CANCEL_RECIPIENT,
      role,
    });

    expect(body).not.toContain('If you also want to cancel');
    expect(body).not.toContain('If you do nothing');
    expect(body).toContain(
      "If the other party does not also ask, the buyer's request alone",
    );
    expect(body).toContain(
      '2026-11-14 10:00:00 Vietnam time (2026-11-14T03:00:00.000Z UTC)',
    );
    expect(body).toContain('every cancel request lapses.');
  });

  it('converts an instant sent with an offset', () => {
    expect(
      activationCancelBody(
        { ...command, oneSidedCancelAllowedFrom: '2026-11-14T10:00:00+07:00' },
        CANCEL_RECIPIENT,
      ),
    ).toContain(
      '2026-11-14 10:00:00 Vietnam time (2026-11-14T03:00:00.000Z UTC)',
    );
  });

  it.each(['BUYER', 'SELLER', 'OPERATOR'] as const)(
    'keeps every line within 76 characters for a %s recipient',
    (role) => {
      const body = activationCancelBody(
        { ...command, requestedByRole: role === 'BUYER' ? 'SELLER' : 'BUYER' },
        { ...CANCEL_RECIPIENT, role },
      );
      for (const line of body.split('\n')) {
        expect(line.length).toBeLessThanOrEqual(76);
      }
    },
  );
});
