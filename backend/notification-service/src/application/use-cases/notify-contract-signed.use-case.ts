import type { ContractSignedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, extracted
 * from the Java source, guards them. A null value renders as "null", as Java
 * string concatenation does.
 */

export class NotifyContractSignedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: ContractSignedCommand): Promise<void> {
    const subject = '[AgriContract] Contract has been signed';
    const body = `Contract ${command.contractId} has been successfully signed.`;

    await this.sender.send(command.eventId, command.buyerEmail, subject, body);
    await this.sender.send(command.eventId, command.sellerEmail, subject, body);
  }
}
