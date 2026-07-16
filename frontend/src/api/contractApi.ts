import apiClient from './client'
import { env } from '../config/env'
import type {
  Contract,
  ContractSignature,
  ContractTerms,
  CreateContractInput,
  NegotiationHistory,
  NegotiationHistoryEntry,
} from '../types/contract'
import { repairMojibake } from '../utils/textEncoding'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

type ContractWire = Omit<Contract, 'signatures' | 'termsRevision'> & {
  revision?: number
  termsRevision?: number
  signatures?: Array<ContractSignature | string>
  signatories?: string[]
  buyerSigned?: boolean
  sellerSigned?: boolean
}

interface PaginatedContractResponse {
  content: ContractWire[]
}

interface NegotiationHistoryEntryWire {
  revision?: number
  termsRevision?: number
  proposedBy?: string
  proposedAt?: string | null
  occurredAt?: string | null
  createdAt?: string | null
  terms?: ContractTerms
  proposedTerms?: ContractTerms
  signatures?: Array<ContractSignature | string>
  signatories?: string[]
  status?: string | null
}

const normalizeSignatures = (
  contract: ContractWire,
  termsRevision: number | undefined,
): ContractSignature[] | undefined => {
  const hasSignaturePayload = contract.signatures !== undefined
    || contract.signatories !== undefined
    || contract.buyerSigned !== undefined
    || contract.sellerSigned !== undefined

  if (!hasSignaturePayload) return undefined

  const signatures = (contract.signatures ?? []).map((signature) =>
    typeof signature === 'string'
      ? { userId: signature, termsRevision }
      : { ...signature, termsRevision: signature.termsRevision ?? termsRevision },
  )
  const legacySignatories = [
    ...(contract.signatories ?? []),
    ...(contract.buyerSigned ? [contract.buyerId] : []),
    ...(contract.sellerSigned ? [contract.sellerId] : []),
  ]

  for (const userId of legacySignatories) {
    if (!signatures.some((signature) => signature.userId === userId)) {
      signatures.push({ userId, termsRevision })
    }
  }

  return signatures
}

const toContract = (contract: ContractWire): Contract => {
  const termsRevision = contract.termsRevision ?? contract.revision
  const signatures = normalizeSignatures(contract, termsRevision)

  return {
    contractId: contract.contractId,
    listingId: contract.listingId,
    buyerId: contract.buyerId,
    sellerId: contract.sellerId,
    productName: repairMojibake(contract.productName),
    buyerOrgName: repairMojibake(contract.buyerOrgName),
    sellerOrgName: repairMojibake(contract.sellerOrgName),
    buyerEmail: contract.buyerEmail,
    sellerEmail: contract.sellerEmail,
    cancelReason: repairMojibake(contract.cancelReason),
    cancelledBy: contract.cancelledBy,
    status: contract.status,
    terms: {
      ...contract.terms,
      qualitySpec: repairMojibake(contract.terms?.qualitySpec),
    },
    ...(termsRevision === undefined ? {} : { termsRevision }),
    ...(signatures === undefined ? {} : { signatures }),
  }
}

const toNegotiationHistoryEntry = (
  entry: NegotiationHistoryEntryWire,
  index: number,
): NegotiationHistoryEntry | null => {
  const terms = entry.terms ?? entry.proposedTerms
  if (!terms || !entry.proposedBy) return null

  const termsRevision = entry.termsRevision ?? entry.revision ?? index + 1
  const signatures = (entry.signatures ?? entry.signatories ?? []).map((signature) =>
    typeof signature === 'string'
      ? { userId: signature, termsRevision }
      : { ...signature, termsRevision: signature.termsRevision ?? termsRevision },
  )

  return {
    termsRevision,
    proposedBy: entry.proposedBy,
    proposedAt: entry.proposedAt ?? entry.occurredAt ?? entry.createdAt,
    terms: {
      ...terms,
      qualitySpec: repairMojibake(terms.qualitySpec),
    },
    ...(entry.signatures === undefined && entry.signatories === undefined ? {} : { signatures }),
    status: entry.status,
  }
}

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
    const data = response.data.data as ContractWire[] | PaginatedContractResponse
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
    return toContract(response.data.data as ContractWire)
  },

  async getNegotiationHistory(contractId: string): Promise<NegotiationHistory> {
    if (env.useMocks) {
      await wait(120)
      const contract = mockContracts.find((item) => item.contractId === contractId)
      if (!contract) return { supported: true, entries: [] }
      return {
        supported: true,
        entries: [{
          termsRevision: contract.termsRevision ?? 1,
          proposedBy: contract.buyerId,
          proposedAt: null,
          terms: contract.terms,
          signatures: contract.signatures,
          status: contract.status,
        }],
      }
    }

    const response = await apiClient.get(`/api/v1/contracts/${contractId}/negotiations`, {
      validateStatus: (status) => (status >= 200 && status < 300) || status === 403 || status === 404,
    })
    if (response.status === 403 || response.status === 404) {
      return { supported: false, entries: [] }
    }

    const data = response.data.data as
      | NegotiationHistoryEntryWire[]
      | { content?: NegotiationHistoryEntryWire[]; entries?: NegotiationHistoryEntryWire[]; supported?: boolean }
    const rawEntries = Array.isArray(data) ? data : data.entries ?? data.content ?? []
    const entries = rawEntries
      .map(toNegotiationHistoryEntry)
      .filter((entry): entry is NegotiationHistoryEntry => entry !== null)

    return {
      supported: Array.isArray(data) ? true : data.supported ?? true,
      entries,
    }
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
    return toContract(response.data.data as ContractWire)
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

  async negotiate(contractId: string, newTerms: ContractTerms) {
    if (env.useMocks) {
      await wait(200)
      mockContracts = mockContracts.map((item) =>
        item.contractId === contractId
          ? {
              ...item,
              terms: newTerms,
              status: 'NEGOTIATING',
            }
          : item,
      )
      return mockContracts.find((item) => item.contractId === contractId)!
    }

    const response = await apiClient.put(`/api/v1/contracts/${contractId}/negotiate`, { newTerms })
    return toContract(response.data.data as ContractWire)
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
    return toContract(response.data.data as ContractWire)
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
    return toContract(response.data.data as ContractWire)
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
    return toContract(response.data.data as ContractWire)
  },
}
