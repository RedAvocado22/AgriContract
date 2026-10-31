import type { EscrowLockedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

export class NotifyEscrowLockedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: EscrowLockedCommand): Promise<void> {
    const subject = '[AgriContract] Escrow locked — proceed with delivery';
    const body = `Escrow ${command.escrowId} has been successfully locked.`;

    await this.sender.send(command.eventId, command.sellerEmail, subject, body);
  }
}
