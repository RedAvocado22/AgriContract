import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { contractApi } from '../api/contractApi'
import { useContractsQuery } from '../hooks/useContractsQuery'
import { useAuthStore } from '../stores/authStore'
import type { Contract, ContractStatus } from '../types/contract'
import { formatDate, formatMoney, formatPercent } from '../utils/formatters'

const STATUS_LABELS: Record<ContractStatus, string> = {
  OFFERED: 'Offered',
  NEGOTIATING: 'Negotiating',
  SIGNED: 'Signed',
  ACTIVE: 'Active',
  DELIVERED: 'Delivered',
  SETTLED: 'Settled',
  CANCELLED: 'Cancelled',
  DISPUTED: 'Disputed',
}

const canSign = (contract: Contract) => ['OFFERED', 'NEGOTIATING'].includes(contract.status)
const canConfirmDelivery = (contract: Contract) => contract.status === 'ACTIVE'
const canDispute = (contract: Contract) => ['ACTIVE', 'DELIVERED'].includes(contract.status)
const canCancel = (contract: Contract) => ['OFFERED', 'NEGOTIATING'].includes(contract.status)

export function ContractsPage() {
  const queryClient = useQueryClient()
  const role = useAuthStore((state) => state.user?.role)
  const normalizedRole = role?.trim().toUpperCase()
  const [status, setStatus] = useState<'ALL' | ContractStatus>('ALL')
  const [reasonById, setReasonById] = useState<Record<string, string>>({})
  const { data, isLoading, isError } = useContractsQuery(normalizedRole === 'SELLER' ? 'SELLER' : 'BUYER')

  const actionMutation = useMutation({
    mutationFn: async ({
      contractId,
      action,
      reason,
    }: {
      contractId: string
      action: 'sign' | 'confirm' | 'cancel' | 'dispute'
      reason?: string
    }) => {
      if (action === 'sign') {
        return contractApi.sign(contractId)
      }
      if (action === 'confirm') {
        return contractApi.confirmDelivery(contractId)
      }
      if (action === 'cancel') {
        return contractApi.cancel(contractId, reason || 'Cancelled from frontend')
      }
      return contractApi.dispute(contractId, reason || 'Dispute raised from frontend')
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['contracts'] })
    },
  })

  const contracts = useMemo(() => {
    const items = data ?? []
    if (status === 'ALL') {
      return items
    }
    return items.filter((item) => item.status === status)
  }, [data, status])

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div>
          <h1>Contracts</h1>
          <p>Review offers, signatures, delivery confirmation, cancellation, and disputes.</p>
        </div>
        <Link className="secondary-button" to="/listings">
          <span className="material-symbols-outlined">add</span>
          Start from listing
        </Link>
      </section>

      <section className="content-panel">
        <div className="content-panel__toolbar">
          <div className="segmented-control" aria-label="Contract status filter">
            {(['ALL', 'OFFERED', 'SIGNED', 'ACTIVE', 'DELIVERED', 'DISPUTED'] as const).map(
              (item) => (
                <button
                  key={item}
                  className={status === item ? 'segmented-control__item active' : 'segmented-control__item'}
                  type="button"
                  onClick={() => setStatus(item)}
                >
                  {item === 'ALL' ? 'All' : STATUS_LABELS[item]}
                </button>
              ),
            )}
          </div>
        </div>

        {isLoading ? <div className="empty-state">Loading contracts...</div> : null}
        {isError ? <div className="empty-state">Contracts could not be loaded.</div> : null}
        {!isLoading && !isError && contracts.length === 0 ? (
          <div className="empty-state">No contracts match this view.</div>
        ) : null}

        <div className="contract-list">
          {contracts.map((contract) => (
            <article className="contract-card" key={contract.contractId}>
              <div className="contract-card__main">
                <div>
                  <span className={`status-badge status-badge--${contract.status.toLowerCase()}`}>
                    {STATUS_LABELS[contract.status]}
                  </span>
                  <h3>{contract.productName}</h3>
                  <p>
                    {contract.buyerOrgName} with {contract.sellerOrgName}
                  </p>
                </div>

                <dl className="detail-list">
                  <div>
                    <dt>Quantity</dt>
                    <dd>
                      {contract.terms.quantity.value} {contract.terms.quantity.unit}
                    </dd>
                  </div>
                  <div>
                    <dt>Agreed price</dt>
                    <dd>{formatMoney(contract.terms.agreedPrice.amount, contract.terms.agreedPrice.currency)}</dd>
                  </div>
                  <div>
                    <dt>Delivery</dt>
                    <dd>{formatDate(contract.terms.deliveryDeadline)}</dd>
                  </div>
                  <div>
                    <dt>Seller deposit</dt>
                    <dd>{formatPercent(contract.terms.sellerDepositRate)}</dd>
                  </div>
                </dl>

                <p className="contract-card__quality">{contract.terms.qualitySpec}</p>
              </div>

              <div className="contract-card__actions">
                <Link className="secondary-button secondary-button--full" to={`/escrow?contractId=${contract.contractId}`}>
                  <span className="material-symbols-outlined">account_balance_wallet</span>
                  Escrow
                </Link>
                {canSign(contract) ? (
                  <button
                    className="primary-button primary-button--full"
                    type="button"
                    disabled={actionMutation.isPending}
                    onClick={() => actionMutation.mutate({ contractId: contract.contractId, action: 'sign' })}
                  >
                    Sign
                  </button>
                ) : null}
                {canConfirmDelivery(contract) ? (
                  <button
                    className="primary-button primary-button--full"
                    type="button"
                    disabled={actionMutation.isPending}
                    onClick={() => actionMutation.mutate({ contractId: contract.contractId, action: 'confirm' })}
                  >
                    Confirm delivery
                  </button>
                ) : null}
                {(canCancel(contract) || canDispute(contract)) && (
                  <label className="compact-field">
                    <span>Reason</span>
                    <textarea
                      value={reasonById[contract.contractId] ?? ''}
                      onChange={(event) =>
                        setReasonById((current) => ({
                          ...current,
                          [contract.contractId]: event.target.value,
                        }))
                      }
                      placeholder="Add context for cancel or dispute"
                    />
                  </label>
                )}
                {canCancel(contract) ? (
                  <button
                    className="secondary-button secondary-button--full"
                    type="button"
                    disabled={actionMutation.isPending}
                    onClick={() =>
                      actionMutation.mutate({
                        contractId: contract.contractId,
                        action: 'cancel',
                        reason: reasonById[contract.contractId],
                      })
                    }
                  >
                    Cancel
                  </button>
                ) : null}
                {canDispute(contract) ? (
                  <button
                    className="danger-button"
                    type="button"
                    disabled={actionMutation.isPending}
                    onClick={() =>
                      actionMutation.mutate({
                        contractId: contract.contractId,
                        action: 'dispute',
                        reason: reasonById[contract.contractId],
                      })
                    }
                  >
                    Raise dispute
                  </button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}
