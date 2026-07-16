import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { ContractActionsBar } from '../components/contracts/ContractActionsBar'
import { useContractsQuery } from '../hooks/useContractsQuery'
import { useAuthStore } from '../stores/authStore'
import type { ContractStatus } from '../types/contract'
import { formatDate, formatMoney, formatPercent } from '../utils/formatters'

const STATUS_LABELS: Record<ContractStatus, string> = {
  OFFERED: 'Đã gửi đề nghị', NEGOTIATING: 'Đang thương lượng', SIGNED: 'Đã ký', ACTIVE: 'Đang thực hiện',
  DELIVERED: 'Đã giao hàng', SETTLED: 'Đã tất toán', CANCELLED: 'Đã hủy', DISPUTED: 'Đang tranh chấp',
}

const FILTERS: Array<'ALL' | ContractStatus> = ['ALL', 'OFFERED', 'NEGOTIATING', 'SIGNED', 'ACTIVE', 'DELIVERED', 'SETTLED', 'CANCELLED', 'DISPUTED']

export function ContractsPage() {
  const role = useAuthStore((state) => state.user?.role)
  const normalizedRole = role?.trim().toUpperCase()
  const [status, setStatus] = useState<'ALL' | ContractStatus>('ALL')
  const { data, isLoading, isError } = useContractsQuery(normalizedRole === 'SELLER' ? 'SELLER' : 'BUYER')
  const contracts = useMemo(() => status === 'ALL' ? (data ?? []) : (data ?? []).filter((item) => item.status === status), [data, status])

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div><h1>Hợp đồng</h1><p>Theo dõi thương lượng, chữ ký, thực hiện, hủy và tranh chấp.</p></div>
        <Link className="secondary-button" to="/listings"><span className="material-symbols-outlined">add</span>Bắt đầu từ tin hàng</Link>
      </section>

      <section className="content-panel">
        <div className="content-panel__toolbar">
          <div className="segmented-control" aria-label="Lọc trạng thái hợp đồng">
            {FILTERS.map((item) => (
              <button key={item} className={status === item ? 'segmented-control__item active' : 'segmented-control__item'} type="button" onClick={() => setStatus(item)}>
                {item === 'ALL' ? 'Tất cả' : STATUS_LABELS[item]}
              </button>
            ))}
          </div>
        </div>
        {isLoading ? <div className="empty-state">Đang tải hợp đồng...</div> : null}
        {isError ? <div className="empty-state">Không tải được hợp đồng.</div> : null}
        {!isLoading && !isError && contracts.length === 0 ? <div className="empty-state">Không có hợp đồng phù hợp.</div> : null}

        <div className="contract-list">
          {contracts.map((contract) => (
            <article className="contract-card" key={contract.contractId}>
              <div className="contract-card__main">
                <div><span className={`status-badge status-badge--${contract.status.toLowerCase()}`}>{STATUS_LABELS[contract.status]}</span><h3>{contract.productName}</h3><p>{contract.buyerOrgName} với {contract.sellerOrgName}</p></div>
                <dl className="detail-list">
                  <div><dt>Số lượng</dt><dd>{contract.terms.quantity.value} {contract.terms.quantity.unit}</dd></div>
                  <div><dt>Giá</dt><dd>{formatMoney(contract.terms.agreedPrice.amount, contract.terms.agreedPrice.currency)}</dd></div>
                  <div><dt>Giao hàng</dt><dd>{formatDate(contract.terms.deliveryDeadline)}</dd></div>
                  <div><dt>Cọc bên bán</dt><dd>{formatPercent(contract.terms.sellerDepositRate)}</dd></div>
                </dl>
              </div>
              <div className="contract-card__actions">
                <Link className="primary-button primary-button--full" to={`/contracts/${contract.contractId}`}>Xem và xử lý</Link>
                <Link className="secondary-button secondary-button--full" to={`/escrow?contractId=${contract.contractId}`}>Ký quỹ</Link>
                <ContractActionsBar contract={contract} />
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}
