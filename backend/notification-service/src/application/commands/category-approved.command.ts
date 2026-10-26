/** `category.approved`, mirrors the Java CategoryApprovedCommand. */
export interface CategoryApprovedCommand {
  eventId: string;
  /** Carried by the Java DTO but used by no template, so not required. */
  categoryId: string | null;
  name: string;
  proposedByEmail: string;
}
