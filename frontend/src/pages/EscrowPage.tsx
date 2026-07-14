import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { escrowApi } from '../api/escrowApi'
import { useContractsQuery } from '../hooks/useContractsQuery'
import { useEscrowByContractQuery, useEscrowTransactionsQuery } from '../hooks/useEscrowQuery'
import { useAuthStore } from '../stores/authStore'
import { formatDate, formatMoney } from '../utils/formatters'

const ESCROW_STATUS_LABELS: Record<string, string> = {
  BUYER_LOCKED: 'Buyer locked',
  FULLY_LOCKED: 'Fully locked',
  RELEASED: 'Released',
  PENALIZED_BUYER: 'Buyer penalized',
  PENALIZED_SELLER: 'Seller penalized',
  ARBITRATED: 'Arbitrated',
}

const TRANSACTION_LABELS: Record<string, string> = {
  LOCK: 'Lock',
  REFUND_TO_BUYER: 'Refund to buyer',
  REFUND_TO_SELLER: 'Refund to seller',
  RELEASE: 'Release',
  PENALIZE_BUYER: 'Buyer penalty',
  PENALIZE_SELLER: 'Seller penalty',
  ARBITRATION_BUYER: 'Arbitration buyer',
  ARBITRATION_SELLER: 'Arbitration seller',
}

export function EscrowPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const role = useAuthStore((state) => state.user?.role)
  const normalizedRole = role?.trim().toUpperCase()
  const { data: contracts } = useContractsQuery(normalizedRole === 'SELLER' ? 'SELLER' : 'BUYER')
  const initialContractId = searchParams.get('contractId') ?? ''
  const [selectedContractId, setSelectedContractId] = useState(initialContractId)
  const effectiveContractId = selectedContractId || contracts?.[0]?.contractId || ''
  const selectedContract = useMemo(
    () => contracts?.find((contract) => contract.contractId === effectiveContractId),
    [contracts, effectiveContractId],
  )
  const escrowQuery = useEscrowByContractQuery(effectiveContractId)
  const transactionQuery = useEscrowTransactionsQuery(escrowQuery.data?.escrowId)

  const confirmDepositMutation = useMutation({
    mutationFn: (contractId: string) => escrowApi.confirmDeposit(contractId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['escrow'] })
      await queryClient.invalidateQueries({ queryKey: ['escrow-transactions'] })
    },
  })

  const handleContractChange = (contractId: string) => {
    setSelectedContractId(contractId)
    setSearchParams(contractId ? { contractId } : {})
  }

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div>
          <h1>Escrow</h1>
          <p>Track locked buyer funds, seller deposits, and ledger activity by contract.</p>
        </div>
        <select
          className="page-select"
          value={effectiveContractId}
          onChange={(event) => handleContractChange(event.target.value)}
        >
          {(contracts ?? []).map((contract) => (
            <option key={contract.contractId} value={contract.contractId}>
              {contract.productName} - {contract.contractId}
            </option>
          ))}
        </select>
      </section>

      {!effectiveContractId ? <div className="empty-state">No contracts available for escrow lookup.</div> : null}

      {effectiveContractId ? (
        <section className="detail-grid">
          <div className="panel-card">
            <div className="panel-card__header">
              <span className="eyebrow">Contract</span>
              <h3>{selectedContract?.productName ?? effectiveContractId}</h3>
              <p>{selectedContract?.terms.qualitySpec ?? 'Escrow account lookup by contract ID.'}</p>
            </div>

            {escrowQuery.isLoading ? <div className="empty-state">Loading escrow account...</div> : null}
            {escrowQuery.isError ? (
              <div className="empty-state">No escrow account has been created for this contract yet.</div>
            ) : null}

            {escrowQuery.data ? (
              <dl className="detail-list">
                <div>
                  <dt>Status</dt>
                  <dd>
                    <span className={`status-badge status-badge--${escrowQuery.data.status.toLowerCase()}`}>
                      {ESCROW_STATUS_LABELS[escrowQuery.data.status]}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Total amount</dt>
                  <dd>{formatMoney(escrowQuery.data.totalAmount, escrowQuery.data.currency)}</dd>
                </div>
                <div>
                  <dt>Seller deposit</dt>
                  <dd>{formatMoney(escrowQuery.data.sellerDeposit, escrowQuery.data.currency)}</dd>
                </div>
                <div>
                  <dt>Escrow ID</dt>
                  <dd>{escrowQuery.data.escrowId}</dd>
                </div>
              </dl>
            ) : null}

            {normalizedRole === 'SELLER' && escrowQuery.data?.status === 'BUYER_LOCKED' ? (
              <button
                className="primary-button primary-button--full"
                type="button"
                disabled={confirmDepositMutation.isPending}
                onClick={() => confirmDepositMutation.mutate(effectiveContractId)}
              >
                Confirm seller deposit
              </button>
            ) : null}
          </div>

          <div className="panel-card">
            <div className="panel-card__header">
              <span className="eyebrow">Ledger</span>
              <h3>Transactions</h3>
            </div>

            {transactionQuery.isLoading ? <div className="empty-state">Loading ledger...</div> : null}
            {!transactionQuery.isLoading && transactionQuery.data?.length === 0 ? (
              <div className="empty-state">No escrow transactions recorded yet.</div>
            ) : null}

            <div className="activity-list">
              {transactionQuery.data?.map((transaction) => (
                <div key={transaction.transactionId}>
                  <span className="material-symbols-outlined">receipt_long</span>
                  <p>
                    {TRANSACTION_LABELS[transaction.type]} -{' '}
                    {formatMoney(transaction.amount, transaction.currency)}
                    <small>{transaction.note}</small>
                  </p>
                  <small>{formatDate(transaction.createdAt)}</small>
                </div>
              ))}
            </div>
          </div>
        </section>
      ) : null}
    </div>
  )
}
