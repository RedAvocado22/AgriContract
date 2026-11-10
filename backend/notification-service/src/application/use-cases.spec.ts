import {
  FakeEmailPort,
  InMemoryNotificationLogRepository,
} from '../../test/support/fakes';
import {
  readFixture,
  type JavaTemplate,
} from '../../test/support/java-templates';
import { NotificationStatus } from '../domain/notification-log';
import { NotificationSender } from './notification-sender';
import { PHASE1_KINDS } from './phase1-notification-kinds';
import {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from './use-cases';

const fixture = readFixture();

interface UseCase {
  execute(command: never): Promise<void>;
}

/** The use case for each Java handler method of ProcessNotificationUseCaseImpl. */
function useCasesBy(sender: NotificationSender): Record<string, UseCase> {
  return {
    handleEscrowLocked: new NotifyEscrowLockedUseCase(sender),
    handleEscrowReleased: new NotifyEscrowReleasedUseCase(sender),
    handleEscrowPenalized: new NotifyEscrowPenalizedUseCase(sender),
    handleCategoryApproved: new NotifyCategoryApprovedUseCase(sender),
    handleCategoryRejected: new NotifyCategoryRejectedUseCase(sender),
  };
}

/** The email kind each handler sends. */
const KINDS: Record<string, { eventType: string; notificationType: string }> = {
  handleEscrowLocked: PHASE1_KINDS.escrowLocked,
  handleEscrowReleased: PHASE1_KINDS.escrowReleased,
  handleEscrowPenalized: PHASE1_KINDS.escrowPenalized,
  handleCategoryApproved: PHASE1_KINDS.categoryApproved,
  handleCategoryRejected: PHASE1_KINDS.categoryRejected,
};

/** One sample command per handler; every field has a distinct value. */
const SAMPLES: Record<string, Record<string, string | null>> = {
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
  return template.recipients.map((r) => String(command[r]));
}

describe('Notify*UseCase (golden fixtures frozen from the Java service)', () => {
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

  it('covers the 5 remaining Java handlers with one use case each', () => {
    expect(Object.keys(fixture).sort()).toEqual(Object.keys(SAMPLES).sort());
    expect(Object.keys(useCases).sort()).toEqual(Object.keys(fixture).sort());
    expect(Object.keys(KINDS).sort()).toEqual(Object.keys(fixture).sort());
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
        expect(
          logs.get({
            eventId: command.eventId as string,
            recipientEmail: to,
            notificationType: KINDS[name].notificationType,
          }),
        ).toMatchObject({
          status: NotificationStatus.SENT,
          eventType: KINDS[name].eventType,
        });
      }
    },
  );

  it('a retry after the second recipient failed does not mail the first one again', async () => {
    const command = {
      eventId: 'evt-retry',
      escrowId: 'esc',
      buyerEmail: 'buyer@example.test',
      sellerEmail: 'seller@example.test',
    };
    email.failFor.add('buyer@example.test');

    await expect(run('handleEscrowReleased', command)).rejects.toThrow(
      'Event evt-retry: email delivery failed (EENVELOPE/550)',
    );
    expect(email.sent.map((m) => m.to)).toEqual(['seller@example.test']);
    expect(
      logs.get({
        eventId: 'evt-retry',
        recipientEmail: 'buyer@example.test',
        notificationType: 'ESCROW_RELEASED',
      }),
    ).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });

    email.failFor.clear();
    await run('handleEscrowReleased', command); // redelivery of the same event

    expect(email.sent.map((m) => m.to)).toEqual([
      'seller@example.test',
      'buyer@example.test',
    ]);
  });

  it('a redelivered event sends nothing once every recipient is SENT', async () => {
    const command = SAMPLES.handleEscrowReleased;
    await run('handleEscrowReleased', command);
    await run('handleEscrowReleased', command);

    expect(email.sent).toHaveLength(2);
  });

  it('an event redelivered across the V2 deploy mails only the recipients not SENT before V2', async () => {
    const command = SAMPLES.handleEscrowReleased;
    const eventId = command.eventId as string;
    logs.seedLegacy(eventId, 'buyer@example.test', NotificationStatus.SENT);
    logs.seedLegacy(eventId, 'seller@example.test', NotificationStatus.FAILED);

    await run('handleEscrowReleased', command);

    expect(email.sent.map((m) => m.to)).toEqual(['seller@example.test']);
  });
});
