export type ContractStatus =
  | 'OFFERED'
  | 'NEGOTIATING'
  | 'SIGNED'
  | 'ACTIVE'
  | 'DELIVERED'
  | 'SETTLED'
  | 'CANCELLED'
  | 'DISPUTED'

export interface QuantityTerm {
  value: number
  unit: string
}

export interface MoneyTerm {
  amount: number
  currency: string
}

export interface ContractTerms {
  quantity: QuantityTerm
  agreedPrice: MoneyTerm
  deliveryDeadline: string
  buyerPenaltyRate: number
  sellerDepositRate: number
  qualitySpec: string
}

export interface Contract {
  contractId: string
  listingId: string
  buyerId: string
  sellerId: string
  productName: string
  buyerOrgName: string
  sellerOrgName: string
  buyerEmail: string
  sellerEmail: string
  terms: ContractTerms
  status: ContractStatus
  cancelReason?: string | null
  cancelledBy?: string | null
}

export interface CreateContractInput {
  contractId?: string
  listingId: string
  terms: ContractTerms
}
