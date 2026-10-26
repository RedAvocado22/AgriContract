import type { ContractDeliveredCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, extracted
 * from the Java source, guards them. A null value renders as "null", as Java
 * string concatenation does.
 */

export class NotifyContractDeliveredUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: ContractDeliveredCommand): Promise<void> {
    const subject = '[AgriContract] Buyer confirmed receipt of goods';
    const body = `Contract ${command.contractId} has been successfully delivered.`;

    await this.sender.send(command.eventId, command.sellerEmail, subject, body);
  }
}
