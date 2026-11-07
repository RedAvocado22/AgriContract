import type { EscrowPenalizedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';
import { PHASE1_KINDS } from '../phase1-notification-kinds';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, frozen
 * from the Java service at tag notification-java-final, guards them. A null
 * value renders as "null", as Java string concatenation does.
 */

const KIND = PHASE1_KINDS.escrowPenalized;

export class NotifyEscrowPenalizedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: EscrowPenalizedCommand): Promise<void> {
    const subject = '[AgriContract] Contract penalty notification';
    const body = `Escrow ${command.escrowId} has been successfully penalized.`;

    await this.sender.send(
      command.eventId,
      KIND,
      command.penalizedPartyEmail,
      subject,
      body,
    );
  }
}
