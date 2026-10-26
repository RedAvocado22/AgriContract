import { existsSync, readFileSync } from 'node:fs';
import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../test/support/fakes';
import {
  JAVA_SOURCE,
  extractJavaTemplates,
  readFixture,
  type JavaTemplate,
} from '../../test/support/java-templates';
import { NotificationStatus } from '../domain/notification-log';
import { NotificationSender } from './notification-sender';
import {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyContractCancelledUseCase,
  NotifyContractDeliveredUseCase,
  NotifyContractDisputedUseCase,
  NotifyContractSignedUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from './use-cases';

const ADMIN = 'ops-admin@example.test';
const fixture = readFixture();

interface UseCase {
  execute(command: never): Promise<void>;
}

/** The use case for each Java handler method of ProcessNotificationUseCaseImpl. */
function useCasesBy(sender: NotificationSender): Record<string, UseCase> {
  return {
    handleContractSigned: new NotifyContractSignedUseCase(sender),
    handleContractCancelled: new NotifyContractCancelledUseCase(sender),
    handleContractDelivered: new NotifyContractDeliveredUseCase(sender),
    handleContractDisputed: new NotifyContractDisputedUseCase(sender, {
      adminEmail: ADMIN,
    }),
    handleEscrowLocked: new NotifyEscrowLockedUseCase(sender),
    handleEscrowReleased: new NotifyEscrowReleasedUseCase(sender),
    handleEscrowPenalized: new NotifyEscrowPenalizedUseCase(sender),
    handleCategoryApproved: new NotifyCategoryApprovedUseCase(sender),
    handleCategoryRejected: new NotifyCategoryRejectedUseCase(sender),
  };
}

/** One sample command per handler; every field has a distinct value. */
const SAMPLES: Record<string, Record<string, string | null>> = {
  handleContractSigned: {
    eventId: 'evt-signed',
    contractId: 'ctr-1',
    buyerEmail: 'buyer@example.test',
    sellerEmail: 'seller@example.test',
  },
  handleContractCancelled: {
    eventId: 'evt-cancelled',
    contractId: 'ctr-2',
    buyerEmail: 'buyer@example.test',
    sellerEmail: 'seller@example.test',
    reason: 'Buyer không thu xếp được vốn đúng hạn',
  },
  handleContractDelivered: {
    eventId: 'evt-delivered',
    contractId: 'ctr-3',
    sellerEmail: 'seller@example.test',
  },
  handleContractDisputed: {
    eventId: 'evt-disputed',
    contractId: 'ctr-4',
    buyerEmail: 'buyer@example.test',
    sellerEmail: 'seller@example.test',
    reason: 'Cà phê ẩm vượt ngưỡng thoả thuận',
  },
  handleEscrowLocked: {
    eventId: 'evt-locked',
    escrowId: 'esc-1',
    sellerEmail: 'seller@example.test',
  },
  handleEscrowReleased: {
    eventId: 'evt-released',
    escrowId: 'esc-2',
    buyerEmail: 'buyer@example.test',
    sellerEmail: 'seller@example.test',
  },
  handleEscrowPenalized: {
    eventId: 'evt-penalized',
    escrowId: 'esc-3',
    penalizedPartyEmail: 'buyer@example.test',
  },
  handleCategoryApproved: {
    eventId: 'evt-approved',
    categoryId: 'cat-1',
    name: 'Cà phê Robusta',
    proposedByEmail: 'seller@example.test',
  },
  handleCategoryRejected: {
    eventId: 'evt-rejected',
    categoryId: 'cat-2',
    name: 'Ca Phe',
    proposedByEmail: 'seller@example.test',
    rejectionReason: 'Trung ten voi danh muc da co',
  },
};

/** Renders a Java template like Java string concatenation (null -> "null"). */
function render(
  template: JavaTemplate,
  command: Record<string, string | null>,
): string {
  return template.body
    .map((part) => ('text' in part ? part.text : String(command[part.field])))
    .join('');
}

function recipientsOf(
  template: JavaTemplate,
  command: Record<string, string | null>,
): string[] {
  return template.recipients.map((r) =>
    r === 'adminEmail' ? ADMIN : String(command[r]),
  );
}

describe('Notify*UseCase (golden fixtures from the Java source)', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let useCases: Record<string, UseCase>;
  const run = (name: string, command: unknown) =>
    useCases[name].execute(command as never);

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    useCases = useCasesBy(new NotificationSender(logs, email));
  });

  it('covers the 9 Java handlers with one use case each', () => {
    expect(Object.keys(fixture).sort()).toEqual(Object.keys(SAMPLES).sort());
    expect(Object.keys(useCases).sort()).toEqual(Object.keys(fixture).sort());
  });

  it.each(Object.keys(fixture))(
    '%s sends the Java subject and body to the Java recipients, in order',
    async (name) => {
      const template = fixture[name];
      const command = SAMPLES[name];
      await run(name, command);

      const body = render(template, command);
      expect(email.sent).toEqual(
        recipientsOf(template, command).map((to) => ({
          to,
          subject: template.subject,
          body,
        })),
      );
      for (const to of recipientsOf(template, command)) {
        expect(logs.get(command.eventId as string, to)?.status).toBe(
          NotificationStatus.SENT,
        );
      }
    },
  );

  it.each(['handleContractCancelled', 'handleContractDisputed'])(
    '%s renders a missing reason as "null", like Java',
    async (name) => {
      await run(name, { ...SAMPLES[name], reason: null });

      expect(email.sent[0].body).toMatch(/ Reason: null$/);
      expect(email.sent[0].body).toBe(
        render(fixture[name], { ...SAMPLES[name], reason: null }),
      );
    },
  );

  it('contract.disputed mails admin, seller, then buyer', async () => {
    await run('handleContractDisputed', {
      eventId: 'evt-d',
      contractId: 'ctr',
      buyerEmail: 'buyer@example.test',
      sellerEmail: 'seller@example.test',
      reason: 'r',
    });

    expect(email.sent.map((m) => m.to)).toEqual([
      ADMIN,
      'seller@example.test',
      'buyer@example.test',
    ]);
  });

  it('a retry after the second recipient failed does not mail the first one again', async () => {
    const command = {
      eventId: 'evt-retry',
      contractId: 'ctr',
      buyerEmail: 'buyer@example.test',
      sellerEmail: 'seller@example.test',
    };
    email.failFor.add('seller@example.test');

    await expect(run('handleContractSigned', command)).rejects.toThrow(
      'Event evt-retry: email delivery failed (EENVELOPE/550)',
    );
    expect(email.sent.map((m) => m.to)).toEqual(['buyer@example.test']);
    expect(logs.get('evt-retry', 'seller@example.test')).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });

    email.failFor.clear();
    await run('handleContractSigned', command); // redelivery of the same event

    expect(email.sent.map((m) => m.to)).toEqual([
      'buyer@example.test',
      'seller@example.test',
    ]);
  });

  it('a redelivered event sends nothing once every recipient is SENT', async () => {
    const command = SAMPLES.handleEscrowReleased;
    await run('handleEscrowReleased', command);
    await run('handleEscrowReleased', command);

    expect(email.sent).toHaveLength(2);
  });
});

// Until the Java service is removed (M5), the committed fixture must match it.
const describeWhileJavaExists = existsSync(JAVA_SOURCE)
  ? describe
  : describe.skip;

describeWhileJavaExists('golden fixture freshness', () => {
  it('test/fixtures/java-templates.json matches the Java source (npm run fixtures:java)', () => {
    expect(extractJavaTemplates(readFileSync(JAVA_SOURCE, 'utf8'))).toEqual(
      fixture,
    );
  });
});
