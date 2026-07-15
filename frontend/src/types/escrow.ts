export type EscrowStatus =
  | 'BUYER_LOCKED'
  | 'FULLY_LOCKED'
  | 'RELEASED'
  | 'PENALIZED_BUYER'
  | 'PENALIZED_SELLER'
  | 'ARBITRATED'

export type EscrowTransactionType =
  | 'LOCK'
  | 'REFUND_TO_BUYER'
  | 'REFUND_TO_SELLER'
  | 'RELEASE'
  | 'PENALIZE_BUYER'
  | 'PENALIZE_SELLER'
  | 'ARBITRATION_BUYER'
  | 'ARBITRATION_SELLER'

export interface EscrowAccount {
  escrowId: string
  contractId: string
  buyerUserId: string
  sellerUserId: string
  totalAmount: number
  sellerDeposit: number
  currency: string
  status: EscrowStatus
}

export interface EscrowTransaction {
  transactionId: string
  type: EscrowTransactionType
  amount: number
  currency: string
  note: string
  createdAt: string
}
