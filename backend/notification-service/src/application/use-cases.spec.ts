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
} from './use-cases';

const fixture = readFixture();

interface UseCase {
  execute(command: never): Promise<void>;
}

/** The use case for each Java handler method of ProcessNotificationUseCaseImpl. */
function useCasesBy(sender: NotificationSender): Record<string, UseCase> {
  return {
    handleCategoryApproved: new NotifyCategoryApprovedUseCase(sender),
    handleCategoryRejected: new NotifyCategoryRejectedUseCase(sender),
  };
}

/** The email kind each handler sends. */
const KINDS: Record<string, { eventType: string; notificationType: string }> = {
  handleCategoryApproved: PHASE1_KINDS.categoryApproved,
  handleCategoryRejected: PHASE1_KINDS.categoryRejected,
};

/** One sample command per handler; every field has a distinct value. */
const SAMPLES: Record<string, Record<string, string | null>> = {
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

  it('covers the 2 remaining Java handlers with one use case each', () => {
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

  it('a redelivered event sends nothing once its recipient is SENT', async () => {
    const command = SAMPLES.handleCategoryApproved;
    await run('handleCategoryApproved', command);
    await run('handleCategoryApproved', command);

    expect(email.sent).toHaveLength(1);
  });

  it('an event redelivered across the V2 deploy does not mail a recipient SENT before V2', async () => {
    const command = SAMPLES.handleCategoryApproved;
    logs.seedLegacy(
      command.eventId as string,
      'seller@example.test',
      NotificationStatus.SENT,
    );

    await run('handleCategoryApproved', command);

    expect(email.sent).toEqual([]);
  });
});
