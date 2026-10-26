/** `contract.cancelled`, mirrors the Java ContractCancelledCommand. */
export interface ContractCancelledCommand {
  eventId: string;
  contractId: string;
  buyerEmail: string;
  sellerEmail: string;
  /** contract-service accepts a cancel without reason; the body then reads "Reason: null", as in Java. */
  reason: string | null;
}
