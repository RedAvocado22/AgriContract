/** `escrow.locked`, mirrors the Java EscrowLockedCommand. */
export interface EscrowLockedCommand {
  eventId: string;
  escrowId: string;
  sellerEmail: string;
}
