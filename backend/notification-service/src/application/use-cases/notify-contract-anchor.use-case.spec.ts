import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  ANCHOR_RECIPIENTS,
  anchorCommand,
} from '../../../test/support/commands';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import { NotifyContractAnchorUseCase } from './notify-contract-anchor.use-case';

const KIND = COMMAND_KINDS.contractAnchor;

function setup() {
  const logs = new InMemoryNotificationLogRepository();
  const email = new FakeEmailPort();
  const useCase = new NotifyContractAnchorUseCase(
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

describe('NotifyContractAnchorUseCase', () => {
  it('emails every recipient of the command, in its order', async () => {
    const { email, useCase } = setup();
    const command = anchorCommand();

    await useCase.execute(command);

    expect(email.sent.map((m) => m.to)).toEqual([
      'buyer@anchor.test',
      'seller@anchor.test',
    ]);
    expect(new Set(email.sent.map((m) => m.subject)).size).toBe(1);
    expect(email.sent[0].subject).toBe(
      `[AgriContract] Signed contract ${command.contractId}`,
    );
  });

  it('carries the signed hash and the whole terms snapshot in the body', async () => {
    const { email, useCase } = setup();
    const command = anchorCommand();

    await useCase.execute(command);

    const body = email.sent[0].body;
    expect(body).toContain(command.contractId);
    expect(body).toContain(command.signedContentHash);
    expect(body).toContain(command.signedAt);
    // The snapshot is rendered whole: every value the hash covers is readable.
    expect(body).toContain('"agreedPrice": "92000.00"');
    expect(body).toContain('"totalContractValue": "110400000.00"');
    expect(JSON.parse(body.slice(body.indexOf('{')))).toEqual(
      command.contractTermsSnapshot,
    );
  });

  it('records one row per recipient with its platform id and the template version', async () => {
    const { logs, useCase } = setup();
    const command = anchorCommand();

    await useCase.execute(command);

    for (const recipient of ANCHOR_RECIPIENTS) {
      const row = logs.get(keyOf(command.eventId, recipient.email));
      expect(row).toMatchObject({
        status: NotificationStatus.SENT,
        eventType: KIND.eventType,
        notificationType: KIND.notificationType,
        templateVersion: KIND.templateVersion,
        recipientUserId: recipient.userId,
        recipientEmail: recipient.email,
      });
    }
  });

  it('keeps a recipient without a platform id as null, not as a string', async () => {
    const { logs, useCase } = setup();
    const command = anchorCommand();
    command.recipients = [
      { userId: null, email: 'contacts@software-buyer.test', role: 'ADMIN' },
    ];

    await useCase.execute(command);

    expect(
      logs.get(keyOf(command.eventId, 'contacts@software-buyer.test')),
    ).toMatchObject({ recipientUserId: null });
  });

  it('a redelivery of the same command sends nothing twice', async () => {
    const { email, useCase } = setup();

    await useCase.execute(anchorCommand());
    await useCase.execute(anchorCommand());

    expect(email.sent).toHaveLength(2);
  });

  /*
   * The whole command is retried, not the single recipient: the sender claims
   * each email on its own, so the redelivery skips the one already SENT.
   */
  it('a failure on the second recipient leaves the first sent and resends only the second', async () => {
    const { logs, email, useCase } = setup();
    email.failFor.add('seller@anchor.test');

    await expect(useCase.execute(anchorCommand())).rejects.toThrow(
      /email delivery failed|Event /,
    );
    expect(email.sent.map((m) => m.to)).toEqual(['buyer@anchor.test']);
    expect(
      logs.get(keyOf(anchorCommand().eventId, 'seller@anchor.test')),
    ).toMatchObject({ status: NotificationStatus.FAILED, retryCount: 1 });

    email.failFor.clear();
    await useCase.execute(anchorCommand());

    expect(email.sent.map((m) => m.to)).toEqual([
      'buyer@anchor.test',
      'seller@anchor.test',
    ]);
  });

  it('two recipients at the same address are one email, not two', async () => {
    const { email, useCase } = setup();
    const command = anchorCommand();
    command.recipients = [
      {
        userId: ANCHOR_RECIPIENTS[0].userId,
        email: 'both@anchor.test',
        role: 'BUYER',
      },
      {
        userId: ANCHOR_RECIPIENTS[1].userId,
        email: 'both@anchor.test',
        role: 'SELLER',
      },
    ];

    await useCase.execute(command);

    expect(email.sent).toHaveLength(1);
  });
});
