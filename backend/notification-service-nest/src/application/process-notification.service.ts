import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '../config/env.validation';
import {
  NotificationChannel,
  NotificationLog,
  NotificationStatus,
} from '../domain/notification-log';
import type {
  CategoryApprovedCommand,
  CategoryRejectedCommand,
  ContractCancelledCommand,
  ContractDeliveredCommand,
  ContractDisputedCommand,
  ContractSignedCommand,
  EscrowLockedCommand,
  EscrowPenalizedCommand,
  EscrowReleasedCommand,
} from './commands';
import { EMAIL_PORT, type EmailPort } from './ports/email.port';
import {
  NOTIFICATION_LOG_REPOSITORY,
  type NotificationLogRepository,
} from './ports/notification-log.repository';

/**
 * Port of the Java ProcessNotificationUseCaseImpl. Subjects, bodies and the
 * order of recipients are copied verbatim; test/fixtures/java-templates.json,
 * extracted from the Java source, guards them. A null value renders as
 * "null", as Java string concatenation does.
 */
@Injectable()
export class ProcessNotificationService {
  private readonly adminEmail: string;

  constructor(
    @Inject(NOTIFICATION_LOG_REPOSITORY)
    private readonly logs: NotificationLogRepository,
    @Inject(EMAIL_PORT) private readonly email: EmailPort,
    config: ConfigService<AppConfig, true>,
  ) {
    this.adminEmail = config.get('adminEmail', { infer: true });
  }

  async handleContractSigned(command: ContractSignedCommand): Promise<void> {
    const subject = '[AgriContract] Contract has been signed';
    const body = `Contract ${command.contractId} has been successfully signed.`;

    await this.sendNotification(
      command.eventId,
      command.buyerEmail,
      subject,
      body,
    );
    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
  }

  async handleContractCancelled(
    command: ContractCancelledCommand,
  ): Promise<void> {
    const subject = '[AgriContract] Contract has been cancelled';
    const body = `Contract ${command.contractId} has been cancelled. Reason: ${command.reason}`;

    await this.sendNotification(
      command.eventId,
      command.buyerEmail,
      subject,
      body,
    );
    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
  }

  async handleContractDelivered(
    command: ContractDeliveredCommand,
  ): Promise<void> {
    const subject = '[AgriContract] Buyer confirmed receipt of goods';
    const body = `Contract ${command.contractId} has been successfully delivered.`;

    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
  }

  async handleContractDisputed(
    command: ContractDisputedCommand,
  ): Promise<void> {
    const subject = '[AgriContract] [ADMIN] New dispute requires resolution';
    const body = `Contract ${command.contractId} has been disputed. Reason: ${command.reason}`;

    await this.sendNotification(
      command.eventId,
      this.adminEmail,
      subject,
      body,
    );
    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
    await this.sendNotification(
      command.eventId,
      command.buyerEmail,
      subject,
      body,
    );
  }

  async handleEscrowLocked(command: EscrowLockedCommand): Promise<void> {
    const subject = '[AgriContract] Escrow locked — proceed with delivery';
    const body = `Escrow ${command.escrowId} has been successfully locked.`;

    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
  }

  async handleEscrowReleased(command: EscrowReleasedCommand): Promise<void> {
    const subject = '[AgriContract] Payment has been released';
    const body = `Escrow ${command.escrowId} has been successfully released.`;

    await this.sendNotification(
      command.eventId,
      command.sellerEmail,
      subject,
      body,
    );
    await this.sendNotification(
      command.eventId,
      command.buyerEmail,
      subject,
      body,
    );
  }

  async handleEscrowPenalized(command: EscrowPenalizedCommand): Promise<void> {
    const subject = '[AgriContract] Contract penalty notification';
    const body = `Escrow ${command.escrowId} has been successfully penalized.`;

    await this.sendNotification(
      command.eventId,
      command.penalizedPartyEmail,
      subject,
      body,
    );
  }

  async handleCategoryApproved(
    command: CategoryApprovedCommand,
  ): Promise<void> {
    const subject = '[AgriContract] Category proposal approved';
    const body = `Your proposed category "${command.name}" has been approved.`;

    await this.sendNotification(
      command.eventId,
      command.proposedByEmail,
      subject,
      body,
    );
  }

  async handleCategoryRejected(
    command: CategoryRejectedCommand,
  ): Promise<void> {
    const subject = '[AgriContract] Category proposal rejected';
    const body = `Your proposed category "${command.name}" has been rejected. Reason: ${command.rejectionReason}`;

    await this.sendNotification(
      command.eventId,
      command.proposedByEmail,
      subject,
      body,
    );
  }

  /**
   * Sends one email for one event to one recipient, at most once (plan §4.3,
   * same steps as the Java sendNotification):
   * 1. a log already SENT for (eventId, recipient) means done;
   * 2. otherwise insert a PENDING log; losing that insert to another delivery
   *    of the same event means that delivery owns the send, so stop;
   *    an existing PENDING or FAILED log is sent again;
   * 3. send, then save SENT, or FAILED with retry_count + 1 and rethrow so the
   *    whole message is retried (recipients already SENT are skipped then).
   */
  async sendNotification(
    eventId: string,
    recipientEmail: string,
    subject: string,
    body: string,
  ): Promise<void> {
    let log = await this.logs.findByEventIdAndRecipient(
      eventId,
      recipientEmail,
    );
    if (log?.status === NotificationStatus.SENT) return;

    if (!log) {
      log = NotificationLog.create(
        eventId,
        recipientEmail,
        NotificationChannel.EMAIL,
        subject,
        body,
      );
      if (!(await this.logs.insert(log))) return;
    }

    let failure: unknown;
    try {
      await this.email.send({ to: recipientEmail, subject, body });
      log.markSent();
    } catch (err) {
      log.markFailed();
      failure = err;
    }
    await this.logs.update(log);
    if (failure !== undefined) {
      throw new Error(
        `Sending "${subject}" to ${recipientEmail} for event ${eventId} failed: ${
          failure instanceof Error ? failure.message : JSON.stringify(failure)
        }`,
        { cause: failure },
      );
    }
  }
}
