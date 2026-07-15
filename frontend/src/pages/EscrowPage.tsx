import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { escrowApi } from '../api/escrowApi'
import { useContractsQuery } from '../hooks/useContractsQuery'
import { useEscrowByContractQuery, useEscrowTransactionsQuery } from '../hooks/useEscrowQuery'
import { useAuthStore } from '../stores/authStore'
import type { ContractStatus } from '../types/contract'
import { formatDate, formatMoney } from '../utils/formatters'

const CONTRACT_STATUS_LABELS: Record<ContractStatus, string> = {
  OFFERED: 'Đang đề nghị',
  NEGOTIATING: 'Đang thương lượng',
  SIGNED: 'Đã ký',
  ACTIVE: 'Đang thực hiện',
  DELIVERED: 'Đã giao hàng',
  SETTLED: 'Đã tất toán',
  CANCELLED: 'Đã hủy',
  DISPUTED: 'Đang tranh chấp',
}

const ESCROW_STATUS_LABELS: Record<string, string> = {
  BUYER_LOCKED: 'Bên mua đã khóa tiền',
  FULLY_LOCKED: 'Đã khóa đủ ký quỹ',
  RELEASED: 'Đã giải ngân',
  PENALIZED_BUYER: 'Phạt bên mua',
  PENALIZED_SELLER: 'Phạt bên bán',
  ARBITRATED: 'Đã phân xử',
}

const TRANSACTION_LABELS: Record<string, string> = {
  LOCK: 'Khóa tiền',
  REFUND_TO_BUYER: 'Hoàn tiền bên mua',
  REFUND_TO_SELLER: 'Hoàn cọc bên bán',
  RELEASE: 'Giải ngân',
  PENALIZE_BUYER: 'Phạt bên mua',
  PENALIZE_SELLER: 'Phạt bên bán',
  ARBITRATION_BUYER: 'Phân xử cho bên mua',
  ARBITRATION_SELLER: 'Phân xử cho bên bán',
}

const getContractOptionLabel = (productName: string, status: ContractStatus) =>
  `${productName} - ${CONTRACT_STATUS_LABELS[status] ?? status}`

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
          <h1>Ký quỹ</h1>
          <p>Theo dõi tiền bên mua, cọc bên bán và sổ giao dịch theo từng hợp đồng.</p>
        </div>
        <select
          className="page-select"
          value={effectiveContractId}
          onChange={(event) => handleContractChange(event.target.value)}
        >
          {(contracts ?? []).map((contract) => (
            <option key={contract.contractId} value={contract.contractId}>
              {getContractOptionLabel(contract.productName, contract.status)}
            </option>
          ))}
        </select>
      </section>

      {!effectiveContractId ? <div className="empty-state">Chưa có hợp đồng để tra cứu ký quỹ.</div> : null}

      {effectiveContractId ? (
        <section className="detail-grid">
          <div className="panel-card">
            <div className="panel-card__header">
              <span className="eyebrow">Hợp đồng</span>
              <h3>{selectedContract?.productName ?? effectiveContractId}</h3>
              <p>{selectedContract?.terms.qualitySpec ?? 'Tra cứu tài khoản ký quỹ theo mã hợp đồng.'}</p>
            </div>

            {escrowQuery.isLoading ? <div className="empty-state">Đang tải tài khoản ký quỹ...</div> : null}
            {escrowQuery.isError ? (
              <div className="empty-state">Hợp đồng này chưa có tài khoản ký quỹ.</div>
            ) : null}

            {escrowQuery.data ? (
              <dl className="detail-list">
                <div>
                  <dt>Trạng thái</dt>
                  <dd>
                    <span className={`status-badge status-badge--${escrowQuery.data.status.toLowerCase()}`}>
                      {ESCROW_STATUS_LABELS[escrowQuery.data.status] ?? escrowQuery.data.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Tổng tiền</dt>
                  <dd>{formatMoney(escrowQuery.data.totalAmount, escrowQuery.data.currency)}</dd>
                </div>
                <div>
                  <dt>Cọc bên bán</dt>
                  <dd>{formatMoney(escrowQuery.data.sellerDeposit, escrowQuery.data.currency)}</dd>
                </div>
                <div>
                  <dt>Mã ký quỹ</dt>
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
                Xác nhận cọc bên bán
              </button>
            ) : null}
          </div>

          <div className="panel-card">
            <div className="panel-card__header">
              <span className="eyebrow">Sổ giao dịch</span>
              <h3>Lịch sử giao dịch</h3>
            </div>

            {transactionQuery.isLoading ? <div className="empty-state">Đang tải sổ giao dịch...</div> : null}
            {!transactionQuery.isLoading && transactionQuery.data?.length === 0 ? (
              <div className="empty-state">Chưa có giao dịch ký quỹ nào.</div>
            ) : null}

            <div className="activity-list">
              {transactionQuery.data?.map((transaction) => (
                <div key={transaction.transactionId}>
                  <span className="material-symbols-outlined">receipt_long</span>
                  <p>
                    {TRANSACTION_LABELS[transaction.type] ?? transaction.type} -{' '}
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
