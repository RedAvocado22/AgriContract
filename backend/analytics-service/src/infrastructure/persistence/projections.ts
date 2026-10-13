import type { MysqlConnectionLike } from '@agricontract/eventing';
import type { IngestionHandler } from '../../application/port.js';

/**
 * MySQL projection handlers registered at startup. Empty in this scaffold on purpose:
 *  - #133 adds contract.signed (dim_contract) and milestone settlement facts;
 *  - #148 adds dispute/remedy and final-role termination facts.
 * Until then those events wait in their durable queues (no data is acked and dropped).
 */
export const HANDLERS: readonly IngestionHandler<MysqlConnectionLike>[] = [];
