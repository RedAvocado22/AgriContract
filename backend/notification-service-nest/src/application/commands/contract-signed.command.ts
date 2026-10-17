/** `contract.signed`, mirrors the Java ContractSignedCommand. */
export interface ContractSignedCommand {
  eventId: string;
  contractId: string;
  buyerEmail: string;
  sellerEmail: string;
}
