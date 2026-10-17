import { existsSync, readFileSync } from 'node:fs';
import { ConfigService } from '@nestjs/config';
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
import { validate, type AppConfig } from '../config/env.validation';
import { NotificationStatus } from '../domain/notification-log';
import { ProcessNotificationService } from './process-notification.service';

const ADMIN = 'ops-admin@example.test';
const fixture = readFixture();

/** Calls the handler named like the Java method (e.g. handleContractSigned). */
function runHandler(
  service: ProcessNotificationService,
  name: string,
  command: unknown,
): Promise<void> {
  const handlers = service as unknown as Record<
    string,
    (command: unknown) => Promise<void>
  >;
  return handlers[name](command);
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

describe('notification handlers (golden fixtures from the Java source)', () => {
  let logs: InMemoryNotificationLogRepository;
  let email: FakeEmailPort;
  let service: ProcessNotificationService;

  beforeEach(() => {
    logs = new InMemoryNotificationLogRepository();
    email = new FakeEmailPort();
    service = new ProcessNotificationService(
      logs,
      email,
      new ConfigService<AppConfig, true>(validate({ ADMIN_EMAIL: ADMIN })),
    );
  });

  it('covers the 9 Java handlers', () => {
    expect(Object.keys(fixture).sort()).toEqual(Object.keys(SAMPLES).sort());
  });

  it.each(Object.keys(fixture))(
    '%s sends the Java subject and body to the Java recipients, in order',
    async (name) => {
      const template = fixture[name];
      const command = SAMPLES[name];
      await runHandler(service, name, command);

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
      await runHandler(service, name, { ...SAMPLES[name], reason: null });

      expect(email.sent[0].body).toMatch(/ Reason: null$/);
      expect(email.sent[0].body).toBe(
        render(fixture[name], { ...SAMPLES[name], reason: null }),
      );
    },
  );

  it('contract.disputed mails admin, seller, then buyer', async () => {
    await service.handleContractDisputed({
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

    await expect(service.handleContractSigned(command)).rejects.toThrow(
      'SMTP refused seller@example.test',
    );
    expect(email.sent.map((m) => m.to)).toEqual(['buyer@example.test']);
    expect(logs.get('evt-retry', 'seller@example.test')).toMatchObject({
      status: NotificationStatus.FAILED,
      retryCount: 1,
    });

    email.failFor.clear();
    await service.handleContractSigned(command); // redelivery of the same event

    expect(email.sent.map((m) => m.to)).toEqual([
      'buyer@example.test',
      'seller@example.test',
    ]);
  });

  it('a redelivered event sends nothing once every recipient is SENT', async () => {
    const command = SAMPLES.handleEscrowReleased;
    await service.handleEscrowReleased(command as never);
    await service.handleEscrowReleased(command as never);

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
