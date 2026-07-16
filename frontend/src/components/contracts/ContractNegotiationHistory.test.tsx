// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { Contract, NegotiationHistory } from '../../types/contract'
import { ContractNegotiationHistory, ContractRevisionSummary } from './ContractNegotiationHistory'

const contract: Contract = {
  contractId: 'contract-1',
  listingId: 'listing-1',
  buyerId: 'buyer-1',
  sellerId: 'seller-1',
  productName: 'Cà phê',
  buyerOrgName: 'Bên mua A',
  sellerOrgName: 'Bên bán B',
  buyerEmail: 'buyer@example.com',
  sellerEmail: 'seller@example.com',
  status: 'NEGOTIATING',
  termsRevision: 2,
  signatures: [{ userId: 'buyer-1', termsRevision: 2 }],
  terms: {
    quantity: { value: 2, unit: 'tấn' },
    agreedPrice: { amount: 200, currency: 'VND' },
    deliveryDeadline: '2026-12-31',
    buyerPenaltyRate: 0.02,
    sellerDepositRate: 0.1,
    qualitySpec: 'Loại 1',
  },
}

afterEach(cleanup)

describe('contract revision compatibility UI', () => {
  it('renders authoritative signature state for the current revision', () => {
    render(<ContractRevisionSummary contract={contract} />)

    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('Đã ký')).toBeInTheDocument()
    expect(screen.getByText('Chờ ký')).toBeInTheDocument()
  })

  it('renders negotiation entries supplied by the new backend endpoint', () => {
    const history: NegotiationHistory = {
      supported: true,
      entries: [{
        termsRevision: 2,
        proposedBy: 'seller-1',
        proposedAt: '2026-07-17T03:00:00Z',
        terms: contract.terms,
        signatures: [],
      }],
    }

    render(
      <ContractNegotiationHistory
        contract={contract}
        history={history}
        isLoading={false}
        isError={false}
      />,
    )

    expect(screen.getByText('Phiên bản 2')).toBeInTheDocument()
    expect(screen.getByText('Bên bán · Bên bán B')).toBeInTheDocument()
    expect(screen.getByText('0/2 bên đã ký phiên bản này')).toBeInTheDocument()
  })

  it('falls back without crashing while the old backend has no history endpoint', () => {
    render(
      <ContractNegotiationHistory
        contract={contract}
        history={undefined}
        isLoading={false}
        isError={false}
      />,
    )

    expect(screen.getByText('Lịch sử đàm phán chưa khả dụng trên máy chủ hiện tại.')).toBeInTheDocument()
  })
})
