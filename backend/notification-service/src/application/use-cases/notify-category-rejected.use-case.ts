import type { CategoryRejectedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

export class NotifyCategoryRejectedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: CategoryRejectedCommand): Promise<void> {
    const subject = '[AgriContract] Category proposal rejected';
    const body = `Your proposed category "${command.name}" has been rejected. Reason: ${command.rejectionReason}`;

    await this.sender.send(
      command.eventId,
      command.proposedByEmail,
      subject,
      body,
    );
  }
}
