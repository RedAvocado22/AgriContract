import type { EscrowReleasedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';
import { PHASE1_KINDS } from '../phase1-notification-kinds';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

const KIND = PHASE1_KINDS.escrowReleased;

export class NotifyEscrowReleasedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: EscrowReleasedCommand): Promise<void> {
    const subject = '[AgriContract] Payment has been released';
    const body = `Escrow ${command.escrowId} has been successfully released.`;

    await this.sender.send(
      command.eventId,
      KIND,
      command.sellerEmail,
      subject,
      body,
    );
    await this.sender.send(
      command.eventId,
      KIND,
      command.buyerEmail,
      subject,
      body,
    );
  }
}
