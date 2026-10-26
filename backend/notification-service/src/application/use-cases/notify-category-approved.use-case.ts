import type { CategoryApprovedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, extracted
 * from the Java source, guards them. A null value renders as "null", as Java
 * string concatenation does.
 */

export class NotifyCategoryApprovedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: CategoryApprovedCommand): Promise<void> {
    const subject = '[AgriContract] Category proposal approved';
    const body = `Your proposed category "${command.name}" has been approved.`;

    await this.sender.send(
      command.eventId,
      command.proposedByEmail,
      subject,
      body,
    );
  }
}
