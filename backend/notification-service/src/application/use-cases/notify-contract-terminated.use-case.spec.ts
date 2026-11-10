import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  WITHDRAW_RECIPIENT,
  terminatedCommand,
} from '../../../test/support/commands';
import { NotificationStatus } from '../../domain/notification-log';
import { TERMINATION_TYPES, type TerminationType } from '../commands';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import {
  NotifyContractTerminatedUseCase,
  offerWithdrawnBody,
  unsupportedTermination,
} from './notify-contract-terminated.use-case';

const KIND = COMMAND_KINDS.offerWithdrawn;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyContractTerminatedUseCase(
    new NotificationSender(logs, email, {
      logger: { error: () => undefined },
    }),
  );
  return { logs, email, useCase };
}

describe('NotifyContractTerminatedUseCase, WITHDRAW_OFFER', () => {
  it('emails the other party, with the contract in the subject', async () => {
    const { email, useCase } = setup();
    const command = terminatedCommand();

    await useCase.execute(command);

    expect(email.sent).toEqual([
      {
        to: WITHDRAW_RECIPIENT.email,
        subject: `[AgriContract] Offer withdrawn for contract ${command.contractId}`,
        body: offerWithdrawnBody(command),
      },
    ]);
  });

  it('records the row with its type, template version and platform id', async () => {
    const { logs, useCase } = setup();
    const command = terminatedCommand();

    await useCase.execute(command);

    expect(
      logs.get({
        eventId: command.eventId,
        recipientEmail: WITHDRAW_RECIPIENT.email,
        notificationType: KIND.notificationType,
      }),
    ).toMatchObject({
      status: NotificationStatus.SENT,
      eventType: 'notification.contract_terminated_requested',
      notificationType: 'OFFER_WITHDRAWN',
      templateVersion: 'offer-withdrawn/v1',
      recipientUserId: WITHDRAW_RECIPIENT.userId,
    });
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(terminatedCommand());
    await useCase.execute(terminatedCommand());

    expect(email.sent).toHaveLength(1);
  });

  it('a failure rethrows and the redelivery sends it', async () => {
    const { email, useCase } = setup();
    email.failFor.add(WITHDRAW_RECIPIENT.email);

    await expect(useCase.execute(terminatedCommand())).rejects.toThrow();
    expect(email.sent).toHaveLength(0);

    email.failFor.clear();
    await useCase.execute(terminatedCommand());
    expect(email.sent).toHaveLength(1);
  });

  it('mails nothing for a command it has no template for', async () => {
    const { email, useCase } = setup();

    await expect(
      useCase.execute({
        ...terminatedCommand(),
        terminationType: 'TERMINATION_FOR_BREACH',
      }),
    ).rejects.toThrow('TERMINATION_FOR_BREACH has no email template yet');
    expect(email.sent).toHaveLength(0);
  });
});

describe('unsupportedTermination', () => {
  it('accepts a withdrawal by either party', () => {
    for (const requestedBy of ['BUYER', 'SELLER'] as const) {
      expect(
        unsupportedTermination({ ...terminatedCommand(), requestedBy }),
      ).toBeNull();
    }
  });

  it.each(TERMINATION_TYPES.filter((t) => t !== 'WITHDRAW_OFFER'))(
    'refuses %s until #278 adds its template',
    (terminationType: TerminationType) => {
      expect(
        unsupportedTermination({ ...terminatedCommand(), terminationType }),
      ).toBe(
        `terminationType ${terminationType} has no email template yet (#278)`,
      );
    },
  );

  it('refuses a withdrawal requested by SYSTEM: the email names the party', () => {
    expect(
      unsupportedTermination({ ...terminatedCommand(), requestedBy: 'SYSTEM' }),
    ).toMatch(/^WITHDRAW_OFFER requested by SYSTEM has no email template/);
  });
});

describe('offerWithdrawnBody (offer-withdrawn/v1)', () => {
  const command = terminatedCommand();

  it('says who withdrew, that the contract cannot be signed, nobody is penalised and the quantity goes back', () => {
    expect(offerWithdrawnBody(command)).toBe(
      [
        'The buyer has withdrawn the offer for contract',
        `${command.contractId}.`,
        '',
        'The offer was withdrawn before both parties had signed it, so the',
        'contract can no longer be signed and will not come into force.',
        '',
        'Nobody is penalised: no deposit had been locked, so no money moves.',
        'The reserved quantity goes back to the listing.',
        '',
      ].join('\n'),
    );
  });

  it('names the seller when the seller withdrew', () => {
    expect(offerWithdrawnBody({ ...command, requestedBy: 'SELLER' })).toMatch(
      /^The seller has withdrawn the offer for contract\n/,
    );
  });

  it('keeps every line within 76 characters', () => {
    for (const requestedBy of ['BUYER', 'SELLER'] as const) {
      for (const line of offerWithdrawnBody({ ...command, requestedBy }).split(
        '\n',
      )) {
        expect(line.length).toBeLessThanOrEqual(76);
      }
    }
  });
});
