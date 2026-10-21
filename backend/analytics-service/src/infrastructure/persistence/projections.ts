import type { MysqlConnectionLike } from '@agricontract/eventing';
import type { ProjectionHandler } from '../../application/port';

/**
 * Projection handlers registered at startup. Empty on purpose in this increment:
 *  - #133 adds contract.signed (dim_contract) and milestone settlement facts;
 *  - #148 adds dispute/remedy and final-role termination facts.
 * Events are consumed, validated, deduplicated and persisted in analytics_ingested_event meanwhile, so
 * nothing is lost and those projections can be built from the stored events.
 */
export const PROJECTIONS: readonly ProjectionHandler<MysqlConnectionLike>[] = [];
