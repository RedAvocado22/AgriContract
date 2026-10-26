/** `contract.disputed`, mirrors the Java ContractDisputedCommand. */
export interface ContractDisputedCommand {
  eventId: string;
  contractId: string;
  buyerEmail: string;
  sellerEmail: string;
  /** contract-service accepts a dispute without reason; the body then reads "Reason: null", as in Java. */
  reason: string | null;
}
