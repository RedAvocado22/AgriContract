import {
  unsupportedTermination,
  type NotifyActivationCancelRequestedUseCase,
  type NotifyContractActivationFailedUseCase,
  type NotifyContractAnchorUseCase,
  type NotifyContractTerminatedUseCase,
} from '../../application/use-cases';
import {
  CONTRACT_TERMINATED_REQUESTED,
  parseActivationCancelRequested,
  parseContractActivationFailedRequested,
  parseContractAnchorRequested,
  parseContractTerminatedRequested,
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
  };
}
