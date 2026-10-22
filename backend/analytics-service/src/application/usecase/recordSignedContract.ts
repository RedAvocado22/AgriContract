import type { ContractDimensionRepository, IngestEventCommand, ProjectionHandler } from '../port';
import { stringField } from './payloadFields';

/** contract.signed -> UPSERT dim_contract (design §3.1). Safe to repeat and to receive out of order. */
export class RecordSignedContract<Tx> implements ProjectionHandler<Tx> {
  readonly eventType = 'contract.signed';

  constructor(private readonly contracts: ContractDimensionRepository<Tx>) {}

  async apply(command: IngestEventCommand, tx: Tx): Promise<void> {
    await this.contracts.upsert(tx, {
      contractId: stringField(command, 'contractId'),
      commodity: stringField(command, 'commodity'),
      buyerId: stringField(command, 'buyerId'),
      sellerId: stringField(command, 'sellerId'),
      totalAmount: stringField(command, 'totalAmount'),
      signedAt: new Date(stringField(command, 'signedAt')),
    });
  }
}
