import type { ContractDeliveredCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';
import { PHASE1_KINDS } from '../phase1-notification-kinds';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

const KIND = PHASE1_KINDS.contractDelivered;

export class NotifyContractDeliveredUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: ContractDeliveredCommand): Promise<void> {
    const subject = '[AgriContract] Buyer confirmed receipt of goods';
    const body = `Contract ${command.contractId} has been successfully delivered.`;

    await this.sender.send(
      command.eventId,
      KIND,
      command.sellerEmail,
      subject,
      body,
    );
  }
}
