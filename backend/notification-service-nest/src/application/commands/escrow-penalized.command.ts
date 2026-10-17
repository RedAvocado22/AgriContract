/**
 * `escrow.penalized`, mirrors the Java EscrowPenalizedCommand: the parser has
 * already picked the penalized party's email (buyer when `penalizedParty` is
 * BUYER, seller otherwise).
 */
export interface EscrowPenalizedCommand {
  eventId: string;
  escrowId: string;
  penalizedPartyEmail: string;
}
