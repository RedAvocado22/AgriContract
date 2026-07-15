import apiClient from './client'
import { env } from '../config/env'
import type { EscrowAccount, EscrowTransaction } from '../types/escrow'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const mockEscrows: EscrowAccount[] = [
  {
    escrowId: 'esc-robusta-001',
    contractId: 'ctr-robusta-001',
    buyerUserId: 'mock-buyer',
    sellerUserId: 'seller-001',
    totalAmount: 1700000000,
    sellerDeposit: 170000000,
    currency: 'VND',
    status: 'FULLY_LOCKED',
  },
  {
    escrowId: 'esc-rice-002',
    contractId: 'ctr-rice-002',
    buyerUserId: 'mock-buyer',
    sellerUserId: 'seller-002',
    totalAmount: 2160000000,
    sellerDeposit: 172800000,
    currency: 'VND',
    status: 'BUYER_LOCKED',
  },
]

const mockTransactions: Record<string, EscrowTransaction[]> = {
  'esc-robusta-001': [
    {
      transactionId: 'txn-001',
      type: 'LOCK',
      amount: 1700000000,
      currency: 'VND',
      note: 'Đã khóa tiền thanh toán của bên mua',
      createdAt: '2026-07-12T09:30:00',
    },
    {
      transactionId: 'txn-002',
      type: 'LOCK',
      amount: 170000000,
      currency: 'VND',
      note: 'Đã xác nhận cọc của bên bán',
      createdAt: '2026-07-12T13:15:00',
    },
  ],
}

export const escrowApi = {
  async getByContractId(contractId: string) {
    if (env.useMocks) {
      await wait(180)
      const escrow = mockEscrows.find((item) => item.contractId === contractId)
      if (!escrow) {
        throw new Error('Không tìm thấy ký quỹ')
      }
      return escrow
    }

    const response = await apiClient.get(`/api/v1/escrows/contract/${contractId}`)
    return response.data.data as EscrowAccount
  },

  async getTransactions(escrowId: string) {
    if (env.useMocks) {
      await wait(180)
      return mockTransactions[escrowId] ?? []
    }

    const response = await apiClient.get(`/api/v1/escrows/${escrowId}/transactions`)
    return response.data.data as EscrowTransaction[]
  },

  async confirmDeposit(contractId: string) {
    if (env.useMocks) {
      await wait(220)
      return
    }

    await apiClient.put(`/api/v1/escrows/${contractId}/confirm-deposit`)
  },
}
