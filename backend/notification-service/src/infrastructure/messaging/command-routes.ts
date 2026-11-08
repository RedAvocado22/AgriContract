import type { NotifyContractAnchorUseCase } from '../../application/use-cases';
import { parseContractAnchorRequested } from './notification-command.parsers';
import type { CommandRoutingKey } from './topology';

/** Injection token of the command routing table built by commandRoutes. */
export const COMMAND_ROUTES = Symbol('COMMAND_ROUTES');

export type CommandRoutes = Record<
  CommandRoutingKey,
  (body: Buffer) => Promise<void>
>;

export interface CommandUseCases {
  contractAnchor: NotifyContractAnchorUseCase;
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
  };
}
