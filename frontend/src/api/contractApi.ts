import apiClient from './client'
import { env } from '../config/env'
import type { Contract, CreateContractInput } from '../types/contract'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

let mockContracts: Contract[] = [
  {
    contractId: 'ctr-robusta-001',
    listingId: 'lst-robusta-daklak',
    buyerId: 'mock-buyer',
    sellerId: 'seller-001',
    productName: 'Dak Lak Robusta coffee',
    buyerOrgName: 'Saigon Agricultural Trading',
    sellerOrgName: 'Dak Lak Coffee Cooperative',
    buyerEmail: 'buyer@example.com',
    sellerEmail: 'seller@example.com',
    status: 'SIGNED',
    terms: {
      quantity: { value: 25, unit: 'ton' },
      agreedPrice: { amount: 68000, currency: 'VND' },
      deliveryDeadline: '2026-11-20',
      buyerPenaltyRate: 0.03,
      sellerDepositRate: 0.1,
      qualitySpec: 'Moisture below 12.5%, grade 1, export packing.',
    },
  },
  {
    contractId: 'ctr-rice-002',
    listingId: 'lst-st25-angiang',
    buyerId: 'mock-buyer',
    sellerId: 'seller-002',
    productName: 'An Giang ST25 rice',
    buyerOrgName: 'Mekong Retail Supply',
    sellerOrgName: 'An Giang Rice Union',
    buyerEmail: 'buyer@example.com',
    sellerEmail: 'rice@example.com',
    status: 'ACTIVE',
    terms: {
      quantity: { value: 80, unit: 'ton' },
      agreedPrice: { amount: 27000, currency: 'VND' },
      deliveryDeadline: '2026-12-05',
      buyerPenaltyRate: 0.02,
      sellerDepositRate: 0.08,
      qualitySpec: 'VietGAP certificate, low broken rate, 50kg bags.',
    },
  },
]

export const contractApi = {
  async getAll(role?: string) {
    if (env.useMocks) {
      await wait(220)
      return mockContracts
    }

    const response = await apiClient.get('/api/v1/contracts', {
      params: role ? { role } : undefined,
    })
    return response.data.data as Contract[]
  },

  async getById(contractId: string) {
    if (env.useMocks) {
      await wait(180)
      const contract = mockContracts.find((item) => item.contractId === contractId)
      if (!contract) {
        throw new Error('Contract not found')
      }
      return contract
    }

    const response = await apiClient.get(`/api/v1/contracts/${contractId}`)
    return response.data.data as Contract
  },

  async create(input: CreateContractInput) {
    if (env.useMocks) {
      await wait(300)
      const contract: Contract = {
        contractId: input.contractId ?? `ctr-${crypto.randomUUID().slice(0, 8)}`,
        listingId: input.listingId,
        buyerId: 'mock-buyer',
        sellerId: 'seller-pending',
        productName: 'New contract offer',
        buyerOrgName: 'Current buyer',
        sellerOrgName: 'Listing seller',
        buyerEmail: 'buyer@example.com',
        sellerEmail: 'seller@example.com',
        terms: input.terms,
        status: 'OFFERED',
      }
      mockContracts = [contract, ...mockContracts]
      return contract
    }

    const response = await apiClient.post('/api/v1/contracts', {
      contractId: input.contractId ?? crypto.randomUUID(),
      listingId: input.listingId,
      terms: input.terms,
    })
    return response.data.data as Contract
  },

  async sign(contractId: string) {
    if (env.useMocks) {
      await wait(200)
      mockContracts = mockContracts.map((item) =>
        item.contractId === contractId ? { ...item, status: 'SIGNED' } : item,
      )
      return
    }

    await apiClient.put(`/api/v1/contracts/${contractId}/sign`)
  },

  async confirmDelivery(contractId: string) {
    if (env.useMocks) {
      await wait(200)
      mockContracts = mockContracts.map((item) =>
        item.contractId === contractId ? { ...item, status: 'DELIVERED' } : item,
      )
      return mockContracts.find((item) => item.contractId === contractId)!
    }

    const response = await apiClient.put(`/api/v1/contracts/${contractId}/confirm-delivery`)
    return response.data.data as Contract
  },

  async cancel(contractId: string, reason: string) {
    if (env.useMocks) {
      await wait(200)
      mockContracts = mockContracts.map((item) =>
        item.contractId === contractId
          ? { ...item, status: 'CANCELLED', cancelReason: reason, cancelledBy: 'USER' }
          : item,
      )
      return mockContracts.find((item) => item.contractId === contractId)!
    }

    const response = await apiClient.put(`/api/v1/contracts/${contractId}/cancel`, { reason })
    return response.data.data as Contract
  },

  async dispute(contractId: string, reason: string) {
    if (env.useMocks) {
      await wait(200)
      mockContracts = mockContracts.map((item) =>
        item.contractId === contractId ? { ...item, status: 'DISPUTED', cancelReason: reason } : item,
      )
      return mockContracts.find((item) => item.contractId === contractId)!
    }

    const response = await apiClient.put(`/api/v1/contracts/${contractId}/dispute`, { reason })
    return response.data.data as Contract
  },
}
