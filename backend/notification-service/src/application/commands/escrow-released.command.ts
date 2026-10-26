/** `escrow.released`, mirrors the Java EscrowReleasedCommand. */
export interface EscrowReleasedCommand {
  eventId: string;
  escrowId: string;
  buyerEmail: string;
  sellerEmail: string;
}
