import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../../test/support/fakes';
import {
  ANCHOR_RECIPIENTS,
  anchorCommand,
  TERMS_SNAPSHOT,
  TERMS_SNAPSHOT_HASH,
} from '../../../test/support/commands';
import {
  RFC8785_HASH,
  RFC8785_INPUT,
  RFC8785_OUTPUT,
  sha256,
} from '../../../test/support/jcs';
import { NotificationStatus } from '../../domain/notification-log';
import { COMMAND_KINDS } from '../notification-command-kinds';
import { NotificationSender } from '../notification-sender';
import { NotifyContractAnchorUseCase } from './notify-contract-anchor.use-case';

const KIND = COMMAND_KINDS.contractAnchor;
const CANONICAL_LABEL = 'Canonical form (this is what the hash covers):';
const READABLE_LABEL = 'Signed terms, laid out for reading:';

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
    const readable = body.slice(body.indexOf(READABLE_LABEL));
    expect(JSON.parse(readable.slice(readable.indexOf('{')))).toEqual(
      command.contractTermsSnapshot,
    );
  });

  /*
   * The check the email invites: copy the printed canonical line, hash it, get
   * signedContentHash. The hashes are written out (computed with the JCS
   * library contract-service uses), never computed by the code under test.
   */
  describe('the printed canonical line hashes to signedContentHash', () => {
    it.each([
      ['the terms fixture', TERMS_SNAPSHOT, TERMS_SNAPSHOT_HASH],
      // RFC 8785 §3.2.4: number formatting, string escapes, key order.
      ['the RFC 8785 sample', JSON.parse(RFC8785_INPUT), RFC8785_HASH],
    ])('%s', async (_name, snapshot, hash) => {
      const { email, useCase } = setup();
      const command = anchorCommand();
      command.contractTermsSnapshot = snapshot as Record<string, unknown>;
      command.signedContentHash = hash;

      await useCase.execute(command);

      const lines = email.sent[0].body.split('\n');
      const canonical = lines[lines.indexOf(CANONICAL_LABEL) + 1];
      expect(sha256(canonical)).toBe(hash);
      // And no other line passes for it: the pretty block is not what is hashed.
      expect(lines.filter((line) => sha256(line) === hash)).toEqual([
        canonical,
      ]);
    });

    it('prints the RFC 8785 sample exactly as the RFC canonicalizes it', async () => {
      const { email, useCase } = setup();
      const command = anchorCommand();
      command.contractTermsSnapshot = JSON.parse(RFC8785_INPUT) as Record<
        string,
        unknown
      >;

      await useCase.execute(command);

      expect(email.sent[0].body.split('\n')).toContain(RFC8785_OUTPUT);
    });
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
