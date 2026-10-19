import type { EscrowPenalizedCommand } from '../commands';
import type { NotificationSender } from '../notification-sender';

/*
 * Subjects, bodies and recipient order are copied verbatim from the Java
 * ProcessNotificationUseCaseImpl; test/fixtures/java-templates.json, extracted
 * from the Java source, guards them. A null value renders as "null", as Java
 * string concatenation does.
 */

export class NotifyEscrowPenalizedUseCase {
  constructor(private readonly sender: NotificationSender) {}

  async execute(command: EscrowPenalizedCommand): Promise<void> {
    const subject = '[AgriContract] Contract penalty notification';
    const body = `Escrow ${command.escrowId} has been successfully penalized.`;

    await this.sender.send(
      command.eventId,
      command.penalizedPartyEmail,
      subject,
      body,
    );
  }
}
