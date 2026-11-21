export type ParticipantRole = "BUYER" | "SELLER"
export type Fields = Record<string, unknown>

export interface Contract {
  id: string
  listingId: string
  buyerId: string
  sellerId: string
  buyerName?: string
  sellerName?: string
  productName?: string
  status: string
  generation: "legacy" | "coffee"
  terms: Fields
  hash: string | null
  reservedQuantity?: string
  milestones: Fields[]
  // Current DTO exposes neither signatures nor a permission/version precondition.
  editability: "unknown"
}
export interface ContractPage {
  content: Contract[]
  page: number
  totalPages: number
  totalElements: number
}
export interface Consent {
  action: string
  version: string
  text: string
  contractId: string
  contentHash: string
}
export interface Challenge {
  otpId: string
  expiresAt: string
}
