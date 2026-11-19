import { NonRetryableError } from '@agricontract/eventing';
import { OperatorEmailNotConfiguredError } from '../../application/notification-errors';
import {
  unsupportedTermination,
  type NotifyActivationCancelRequestedUseCase,
  type NotifyContractActivationFailedUseCase,
  type NotifyContractAnchorUseCase,
  type NotifyContractTerminatedUseCase,
  type NotifyLevel15AssignmentWaitingUseCase,
  type NotifyLevel15InspectorAssignmentUseCase,
  type NotifyUserKycResultUseCase,
  type NotifyUserLockChangedUseCase,
} from '../../application/use-cases';
import {
  CONTRACT_TERMINATED_REQUESTED,
  parseActivationCancelRequested,
  parseContractActivationFailedRequested,
  parseContractAnchorRequested,
  parseContractTerminatedRequested,
  parseLevel15AssignmentWaitingRequested,
  parseLevel15InspectorAssignmentRequested,
  parseUserKycResultRequested,
  parseUserLockChangedRequested,
  unsupportedCommand,
} from './notification-command.parsers';
import type { CommandRoutingKey } from './topology';

/** Injection token of the command routing table built by commandRoutes. */
export const COMMAND_ROUTES = Symbol('COMMAND_ROUTES');

export type CommandRoutes = Record<
  CommandRoutingKey,
  (body: Buffer) => Promise<void>
>;

export interface CommandUseCases {
  contractAnchor: NotifyContractAnchorUseCase;
  activationCancelRequested: NotifyActivationCancelRequestedUseCase;
  contractActivationFailed: NotifyContractActivationFailedUseCase;
  contractTerminated: NotifyContractTerminatedUseCase;
  userKycResult: NotifyUserKycResultUseCase;
  userLockChanged: NotifyUserLockChangedUseCase;
  level15AssignmentWaiting: NotifyLevel15AssignmentWaitingUseCase;
  level15InspectorAssignment: NotifyLevel15InspectorAssignmentUseCase;
}

/**
 * Phase 2 command -> parser -> use case, per queue; the counterpart of
 * legacyRoutes for the envelope commands of notification-commands.yaml. A
 * Record over CommandRoutingKey: the compiler rejects a command of TOPOLOGY
 * left without a route.
 */
export function commandRoutes(u: CommandUseCases): CommandRoutes {
  return {
    'notification.contract_anchor_requested': (b) =>
      u.contractAnchor.execute(parseContractAnchorRequested(b)),
    'notification.activation_cancel_requested': (b) =>
      u.activationCancelRequested.execute(parseActivationCancelRequested(b)),
    'notification.contract_activation_failed_requested': (b) =>
      u.contractActivationFailed.execute(
        parseContractActivationFailedRequested(b),
      ),
    'notification.contract_terminated_requested': async (b) => {
      const command = parseContractTerminatedRequested(b);
      const reason = unsupportedTermination(command);
      if (reason !== null) {
        throw unsupportedCommand(
          CONTRACT_TERMINATED_REQUESTED,
          command.eventId,
          reason,
        );
      }
      await u.contractTerminated.execute(command);
    },
    'notification.user_kyc_result_requested': (b) =>
      u.userKycResult.execute(parseUserKycResultRequested(b)),
    'notification.user_lock_changed_requested': (b) =>
      u.userLockChanged.execute(parseUserLockChangedRequested(b)),
    'notification.level1_5_assignment_waiting_requested': async (b) => {
      const command = parseLevel15AssignmentWaitingRequested(b);
      try {
        await u.level15AssignmentWaiting.execute(command);
      } catch (err) {
        // No retry can find an address before OPERATOR_EMAIL is set and the
        // service restarted: straight to the DLQ, with the message that says so.
        if (err instanceof OperatorEmailNotConfiguredError) {
          throw new NonRetryableError(err.message);
        }
        throw err;
      }
    },
    'notification.level1_5_inspector_assignment_requested': (b) =>
      u.level15InspectorAssignment.execute(
        parseLevel15InspectorAssignmentRequested(b),
      ),
  };
}
