import type { ContractDisputedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';
import type { NotificationSettings } from '../notification-settings';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

export class NotifyContractDisputedUseCase {
  constructor(
    private readonly sender: NotificationSender,
    private readonly settings: NotificationSettings,
  ) {}

  async execute(command: ContractDisputedCommand): Promise<void> {
    const subject = '[AgriContract] [ADMIN] New dispute requires resolution';
    const body = `Contract ${command.contractId} has been disputed. Reason: ${command.reason}`;

    await this.sender.send(
      command.eventId,
      this.settings.adminEmail,
      subject,
      body,
    );
    await this.sender.send(command.eventId, command.sellerEmail, subject, body);
    await this.sender.send(command.eventId, command.buyerEmail, subject, body);
  }
}
