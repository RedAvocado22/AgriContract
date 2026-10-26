/** `contract.delivered`, mirrors the Java ContractDeliveredCommand. */
export interface ContractDeliveredCommand {
  eventId: string;
  contractId: string;
  sellerEmail: string;
}
