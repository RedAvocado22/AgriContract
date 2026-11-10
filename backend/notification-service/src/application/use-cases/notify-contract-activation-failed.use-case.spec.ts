import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  ACTIVATION_FAILED_RECIPIENTS,
  activationFailedCommand,
} from '../../../test/support/commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyContractActivationFailedUseCase,
  activationFailedBody,
} from './notify-contract-activation-failed.use-case';

const KIND = COMMAND_KINDS.contractActivationFailed;
const [BUYER, SELLER] = ACTIVATION_FAILED_RECIPIENTS;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyContractActivationFailedUseCase(
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

describe('NotifyContractActivationFailedUseCase', () => {
  it('emails both parties, with the contract in the subject', async () => {
    const { email, useCase } = setup();
    const command = activationFailedCommand();

    await useCase.execute(command);

    expect(email.sent.map((m) => m.to)).toEqual([BUYER.email, SELLER.email]);
    for (const mail of email.sent) {
      expect(mail.subject).toBe(
        `[AgriContract] Deposits not locked for contract ${command.contractId}`,
      );
    }
  });

  it('records one row per party with its type, template version and platform id', async () => {
    const { logs, useCase } = setup();
    const command = activationFailedCommand();

    await useCase.execute(command);

    for (const party of [BUYER, SELLER]) {
      expect(logs.get(keyOf(command.eventId, party.email))).toMatchObject({
        status: NotificationStatus.SENT,
        eventType: 'notification.contract_activation_failed_requested',
        notificationType: 'CONTRACT_ACTIVATION_FAILED',
        templateVersion: 'contract-activation-failed/v1',
        recipientUserId: party.userId,
      });
    }
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(activationFailedCommand());
    await useCase.execute(activationFailedCommand());

    expect(email.sent).toHaveLength(2);
  });

  it('a later report of the same contract is a new event and is mailed again', async () => {
    const { email, useCase } = setup();

    await useCase.execute(activationFailedCommand());
    await useCase.execute({
      ...activationFailedCommand(),
      eventId: '0f1e2d3c-4b5a-4968-8776-5a4b3c2d1e0f',
    });

    expect(email.sent).toHaveLength(4);
  });

  it('a failure for the seller rethrows; the redelivery sends only the seller', async () => {
    const { email, useCase } = setup();
    email.failFor.add(SELLER.email);

    await expect(useCase.execute(activationFailedCommand())).rejects.toThrow();
    expect(email.sent.map((m) => m.to)).toEqual([BUYER.email]);

    email.failFor.clear();
    await useCase.execute(activationFailedCommand());
    expect(email.sent.map((m) => m.to)).toEqual([BUYER.email, SELLER.email]);
  });
});

describe('activationFailedBody (contract-activation-failed/v1)', () => {
  const command = activationFailedCommand();

  it('tells a party which deposit failed, that nobody is penalised, and what can happen next', () => {
    expect(activationFailedBody(command, BUYER)).toBe(
      [
        'The deposits of contract',
        command.contractId,
        'could not be locked. The contract is signed but has not become active.',
        '',
        "Not locked: the buyer's deposit.",
        'Buyer deposit: not locked, the lock failed.',
        'Seller deposit: locked.',
        '',
        'Nobody breached the contract, so nobody is penalised: no deposit is',
        'forfeited and no reputation score changes.',
        '',
        'What can happen next:',
        '',
        '- An AgriContract operator may retry locking the deposits. If they',
        '  are locked, the contract becomes active as signed and every cancel',
        '  request lapses.',
        '',
        '- You can request to cancel the contract in AgriContract. Once both',
        '  parties have asked, the refund of any locked deposit starts at once.',
        "  From the time below, one party's request alone is enough:",
        '  2026-11-14 00:00:00 Vietnam time (2026-11-13T17:00:00.000Z UTC)',
        '',
      ].join('\n'),
    );
  });

  it('gives the seller the same text', () => {
    expect(activationFailedBody(command, SELLER)).toBe(
      activationFailedBody(command, BUYER),
    );
  });

  it.each([
    ['SELLER', "Not locked: the seller's deposit."],
    ['BOTH', 'Not locked: both deposits.'],
  ] as const)(
    'names the failed deposit for failedLeg %s',
    (failedLeg, line) => {
      expect(activationFailedBody({ ...command, failedLeg }, BUYER)).toContain(
        `\n${line}\n`,
      );
    },
  );

  it('says when the contract has no seller deposit', () => {
    expect(
      activationFailedBody(
        { ...command, sellerDepositState: 'NOT_REQUIRED' },
        BUYER,
      ),
    ).toContain('\nSeller deposit: none, this contract does not require it.\n');
  });

  it.each([
    [
      'a state code it has no words for, as sent',
      'RETURNED',
      'reported as RETURNED',
    ],
    ['an empty state as not reported', '', 'not reported'],
    [
      'free text as not reported, never quoted',
      'bank said: no funds',
      'not reported',
    ],
  ])('shows %s', (_case, state, words) => {
    const body = activationFailedBody(
      { ...command, buyerDepositState: state },
      BUYER,
    );

    expect(body).toContain(`\nBuyer deposit: ${words}.\n`);
    if (state !== '') expect(body.includes(state)).toBe(state === 'RETURNED');
  });

  it('gives a recipient who is not a party "each party can" instead of "you can"', () => {
    const body = activationFailedBody(command, {
      userId: null,
      email: 'ops@activation.test',
      role: 'OPERATOR',
    });

    expect(body).not.toContain('You can');
    expect(body).toContain(
      '- Each party can request to cancel the contract. Once both',
    );
    expect(body).toContain(
      '2026-11-14 00:00:00 Vietnam time (2026-11-13T17:00:00.000Z UTC)',
    );
  });

  it('lists only the next steps the command names', () => {
    const retryOnly = activationFailedBody(
      { ...command, nextActions: ['RETRY_DEPOSIT_LOCK'] },
      BUYER,
    );
    expect(retryOnly).toContain('operator may retry');
    expect(retryOnly).not.toContain('request to cancel');
    expect(retryOnly).not.toContain('Vietnam time');

    const cancelOnly = activationFailedBody(
      { ...command, nextActions: ['REQUEST_ACTIVATION_CANCEL'] },
      BUYER,
    );
    expect(cancelOnly).not.toContain('operator may retry');
    expect(cancelOnly).toContain('You can request to cancel');

    const none = activationFailedBody({ ...command, nextActions: [] }, BUYER);
    expect(none).not.toContain('What can happen next');
    expect(none).toMatch(/no reputation score changes\.\n$/);
  });

  it('converts an instant sent with an offset', () => {
    expect(
      activationFailedBody(
        { ...command, oneSidedCancelAllowedFrom: '2026-11-14T00:00:00+07:00' },
        BUYER,
      ),
    ).toContain(
      '2026-11-14 00:00:00 Vietnam time (2026-11-13T17:00:00.000Z UTC)',
    );
  });

  it.each(['BUYER', 'SELLER', 'BOTH'] as const)(
    'keeps every line within 76 characters for failedLeg %s, party or not',
    (failedLeg) => {
      for (const role of ['BUYER', 'OPERATOR'] as const) {
        const body = activationFailedBody(
          {
            ...command,
            failedLeg,
            buyerDepositState: 'NOT_REQUIRED',
            sellerDepositState: `X${'_'.repeat(39)}`,
          },
          { ...BUYER, role },
        );
        for (const line of body.split('\n')) {
          expect(line.length).toBeLessThanOrEqual(76);
        }
      }
    },
  );
});
