import apiClient from './client'
import { env } from '../config/env'
import type { Contract, CreateContractInput } from '../types/contract'
import { repairMojibake } from '../utils/textEncoding'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

interface PaginatedContractResponse {
  content: Contract[]
}

const toContract = (contract: Contract): Contract => ({
  ...contract,
  productName: repairMojibake(contract.productName),
  buyerOrgName: repairMojibake(contract.buyerOrgName),
  sellerOrgName: repairMojibake(contract.sellerOrgName),
  cancelReason: repairMojibake(contract.cancelReason),
  terms: {
    ...contract.terms,
    qualitySpec: repairMojibake(contract.terms?.qualitySpec),
  },
})

let mockContracts: Contract[] = [
  {
    contractId: 'ctr-robusta-001',
    listingId: 'lst-robusta-daklak',
    buyerId: 'mock-buyer',
    sellerId: 'seller-001',
    productName: 'Cà phê Robusta Đắk Lắk',
    buyerOrgName: 'Công ty Thương mại Nông sản Sài Gòn',
    sellerOrgName: 'HTX Cà phê Đắk Lắk',
    buyerEmail: 'buyer@example.com',
    sellerEmail: 'seller@example.com',
    status: 'SIGNED',
    terms: {
      quantity: { value: 25, unit: 'tấn' },
      agreedPrice: { amount: 68000, currency: 'VND' },
      deliveryDeadline: '2026-11-20',
      buyerPenaltyRate: 0.03,
      sellerDepositRate: 0.1,
      qualitySpec: 'Độ ẩm dưới 12,5%, loại 1, có thể đóng gói xuất khẩu.',
    },
  },
  {
    contractId: 'ctr-rice-002',
    listingId: 'lst-st25-angiang',
    buyerId: 'mock-buyer',
    sellerId: 'seller-002',
    productName: 'Gạo ST25 An Giang',
    buyerOrgName: 'Chuỗi bán lẻ Mekong',
    sellerOrgName: 'Liên hiệp Lúa gạo An Giang',
    buyerEmail: 'buyer@example.com',
    sellerEmail: 'rice@example.com',
    status: 'ACTIVE',
    terms: {
      quantity: { value: 80, unit: 'tấn' },
      agreedPrice: { amount: 27000, currency: 'VND' },
      deliveryDeadline: '2026-12-05',
      buyerPenaltyRate: 0.02,
      sellerDepositRate: 0.08,
      qualitySpec: 'Có chứng nhận VietGAP, tỷ lệ tấm thấp, bao 50kg.',
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
      params: {
        ...(role ? { role } : {}),
        page: 0,
        size: 100,
      },
    })
    const data = response.data.data as Contract[] | PaginatedContractResponse
    return (Array.isArray(data) ? data : data.content).map(toContract)
  },

  async getById(contractId: string) {
    if (env.useMocks) {
      await wait(180)
      const contract = mockContracts.find((item) => item.contractId === contractId)
      if (!contract) {
        throw new Error('Không tìm thấy hợp đồng')
      }
      return contract
    }

    const response = await apiClient.get(`/api/v1/contracts/${contractId}`)
    return toContract(response.data.data as Contract)
  },

  async create(input: CreateContractInput) {
    if (env.useMocks) {
      await wait(300)
      const contract: Contract = {
        contractId: input.contractId ?? `ctr-${crypto.randomUUID().slice(0, 8)}`,
        listingId: input.listingId,
        buyerId: 'mock-buyer',
        sellerId: 'seller-pending',
        productName: 'Đề nghị hợp đồng mới',
        buyerOrgName: 'Bên mua hiện tại',
        sellerOrgName: 'Bên bán của tin hàng',
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
    return toContract(response.data.data as Contract)
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
    return toContract(response.data.data as Contract)
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
    return toContract(response.data.data as Contract)
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
    return toContract(response.data.data as Contract)
  },
}
