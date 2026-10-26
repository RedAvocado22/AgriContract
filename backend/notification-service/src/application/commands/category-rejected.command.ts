/** `category.rejected`, mirrors the Java CategoryRejectedCommand. */
export interface CategoryRejectedCommand {
  eventId: string;
  /** Carried by the Java DTO but used by no template, so not required. */
  categoryId: string | null;
  name: string;
  proposedByEmail: string;
  rejectionReason: string;
}
