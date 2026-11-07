import type { CategoryRejectedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';
import { PHASE1_KINDS } from '../phase1-notification-kinds';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

const KIND = PHASE1_KINDS.categoryRejected;

export class NotifyCategoryRejectedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: CategoryRejectedCommand): Promise<void> {
    const subject = '[AgriContract] Category proposal rejected';
    const body = `Your proposed category "${command.name}" has been rejected. Reason: ${command.rejectionReason}`;

    await this.sender.send(
      command.eventId,
      KIND,
      command.proposedByEmail,
      subject,
      body,
    );
  }
}
