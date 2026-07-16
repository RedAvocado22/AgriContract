// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../../stores/authStore'
import { useContractInteractionStore } from '../../stores/contractInteractionStore'
import type { Contract, ContractStatus } from '../../types/contract'
import { ContractActionsBar } from './ContractActionsBar'

const contract = (status: ContractStatus): Contract => ({
  contractId: 'contract-1',
  listingId: 'listing-1',
  buyerId: 'buyer-1',
  sellerId: 'seller-1',
  productName: 'Cà phê',
  buyerOrgName: 'Bên mua',
  sellerOrgName: 'Bên bán',
  buyerEmail: 'buyer@example.com',
  sellerEmail: 'seller@example.com',
  status,
  terms: {
    quantity: { value: 1, unit: 'tấn' },
    agreedPrice: { amount: 100, currency: 'VND' },
    deliveryDeadline: '2026-12-31',
    buyerPenaltyRate: 0.02,
    sellerDepositRate: 0.1,
    qualitySpec: 'Loại 1',
  },
})

const renderActions = (status: ContractStatus, overrides: Partial<Contract> = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ContractActionsBar contract={{ ...contract(status), ...overrides }} />
    </QueryClientProvider>,
  )
}

const setUser = (id: string, role: 'BUYER' | 'SELLER') => {
  useAuthStore.setState({
    accessToken: 'token',
    isAuthenticated: true,
    profileStatus: 'ready',
    user: { id, role, name: role, email: `${id}@example.com`, organization: role },
  })
}

describe('ContractActionsBar Phase 1 permissions', () => {
  beforeEach(() => useContractInteractionStore.setState({ signedTermsByContractId: new Map() }))
  afterEach(cleanup)

  it('allows both participants to sign an offered contract but not cancel it', () => {
    setUser('seller-1', 'SELLER')
    renderActions('OFFERED')
    expect(screen.getByRole('button', { name: 'Ký điều khoản hiện tại' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hủy hợp đồng' })).not.toBeInTheDocument()
  })

  it('allows only the buyer to confirm delivery on an active contract', () => {
    setUser('buyer-1', 'BUYER')
    const view = renderActions('ACTIVE')
    expect(screen.getByRole('button', { name: 'Xác nhận đã nhận hàng' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hủy hợp đồng' })).toBeInTheDocument()

    view.unmount()
    setUser('seller-1', 'SELLER')
    renderActions('ACTIVE')
    expect(screen.queryByRole('button', { name: 'Xác nhận đã nhận hàng' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hủy hợp đồng' })).toBeInTheDocument()
  })

  it('allows only the buyer to dispute a delivered contract', () => {
    setUser('buyer-1', 'BUYER')
    const view = renderActions('DELIVERED')
    expect(screen.getByRole('button', { name: 'Mở tranh chấp' })).toBeInTheDocument()

    view.unmount()
    setUser('seller-1', 'SELLER')
    renderActions('DELIVERED')
    expect(screen.queryByRole('button', { name: 'Mở tranh chấp' })).not.toBeInTheDocument()
  })

  it('uses the backend signature state for the current terms revision', () => {
    setUser('buyer-1', 'BUYER')
    renderActions('NEGOTIATING', {
      termsRevision: 2,
      signatures: [{ userId: 'buyer-1', termsRevision: 2 }],
    })

    expect(screen.queryByRole('button', { name: 'Ký điều khoản hiện tại' })).not.toBeInTheDocument()
    expect(screen.getByText('Bạn đã ký. Đang chờ bên còn lại ký hoặc gửi điều khoản mới.')).toBeInTheDocument()
  })

  it('allows signing again when the existing signature belongs to an older revision', () => {
    setUser('buyer-1', 'BUYER')
    renderActions('NEGOTIATING', {
      termsRevision: 2,
      signatures: [{ userId: 'buyer-1', termsRevision: 1 }],
    })

    expect(screen.getByRole('button', { name: 'Ký điều khoản hiện tại' })).toBeInTheDocument()
  })

  it('prefers an empty backend signature list over the legacy local fallback', () => {
    setUser('buyer-1', 'BUYER')
    const currentContract = contract('NEGOTIATING')
    useContractInteractionStore.getState().markSigned(
      currentContract.contractId,
      JSON.stringify(currentContract.terms),
    )

    renderActions('NEGOTIATING', { termsRevision: 2, signatures: [] })

    expect(screen.getByRole('button', { name: 'Ký điều khoản hiện tại' })).toBeInTheDocument()
  })
})
