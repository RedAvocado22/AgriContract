import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'

import { contractApi } from '../api/contractApi'
import { ContractActionsBar } from '../components/contracts/ContractActionsBar'
import {
  ContractNegotiationHistory,
  ContractRevisionSummary,
} from '../components/contracts/ContractNegotiationHistory'
import { useContractQuery } from '../hooks/useContractQuery'
import { useNegotiationHistoryQuery } from '../hooks/useNegotiationHistoryQuery'
import { useAuthStore } from '../stores/authStore'
import type { ContractTerms } from '../types/contract'
import { getApiErrorMessage } from '../utils/apiError'
import { formatDate, formatMoney, formatPercent } from '../utils/formatters'

const STATUS_LABELS: Record<string, string> = {
  OFFERED: 'Đã gửi đề nghị', NEGOTIATING: 'Đang thương lượng', SIGNED: 'Đã ký', ACTIVE: 'Đang thực hiện',
  DELIVERED: 'Đã giao hàng', SETTLED: 'Đã tất toán', CANCELLED: 'Đã hủy', DISPUTED: 'Đang tranh chấp',
}

export function ContractDetailPage() {
  const { contractId = '' } = useParams()
  const queryClient = useQueryClient()
  const user = useAuthStore((state) => state.user)
  const { data: contract, isLoading, isError } = useContractQuery(contractId)
  const negotiationHistoryQuery = useNegotiationHistoryQuery(
    contractId,
    contract?.termsRevision !== undefined,
  )
  const [quantity, setQuantity] = useState('')
  const [price, setPrice] = useState('')
  const [deliveryDeadline, setDeliveryDeadline] = useState('')
  const [qualitySpec, setQualitySpec] = useState('')

  useEffect(() => {
    if (!contract) return
    setQuantity(String(contract.terms.quantity.value))
    setPrice(String(contract.terms.agreedPrice.amount))
    setDeliveryDeadline(contract.terms.deliveryDeadline)
    setQualitySpec(contract.terms.qualitySpec)
  }, [contract])

  const negotiateMutation = useMutation({
    mutationFn: async () => {
      if (!contract) throw new Error('Không tìm thấy hợp đồng.')
      const nextQuantity = Number(quantity)
      const nextPrice = Number(price)
      if (nextQuantity <= 0 || nextPrice <= 0 || !deliveryDeadline || !qualitySpec.trim()) {
        throw new Error('Điền đầy đủ điều khoản hợp lệ trước khi gửi đề nghị mới.')
      }
      const newTerms: ContractTerms = {
        ...contract.terms,
        quantity: { ...contract.terms.quantity, value: nextQuantity },
        agreedPrice: { ...contract.terms.agreedPrice, amount: nextPrice },
        deliveryDeadline,
        qualitySpec: qualitySpec.trim(),
      }
      return contractApi.negotiate(contract.contractId, newTerms)
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['contract', contractId] }),
        queryClient.invalidateQueries({ queryKey: ['contracts'] }),
        queryClient.invalidateQueries({ queryKey: ['contract-negotiations', contractId] }),
      ])
    },
  })

  if (!contractId) return <Navigate to="/contracts" replace />
  if (isLoading) return <div className="empty-state">Đang tải hợp đồng...</div>
  if (isError || !contract) return <div className="empty-state">Không tải được hợp đồng.</div>

  const isParticipant = user?.id === contract.buyerId || user?.id === contract.sellerId
  const isAdmin = user?.role?.trim().toUpperCase() === 'ADMIN'
  const canNegotiate = isParticipant && ['OFFERED', 'NEGOTIATING'].includes(contract.status)

  return (
    <div className="page-stack">
      <Link className="inline-link" to="/contracts">← Quay lại danh sách hợp đồng</Link>
      <section className="page-title-row">
        <div>
          <span className={`status-badge status-badge--${contract.status.toLowerCase()}`}>{STATUS_LABELS[contract.status]}</span>
          <h1>{contract.productName}</h1>
          <p>{contract.buyerOrgName} và {contract.sellerOrgName}</p>
        </div>
        <div className="form-actions">
          <Link className="secondary-button" to={`/escrow?contractId=${contract.contractId}`}>Xem ký quỹ</Link>
          {isAdmin && contract.status === 'DISPUTED' ? <Link className="danger-button" to={`/admin/arbitrate/${contract.contractId}`}>Phân xử</Link> : null}
        </div>
      </section>

      {contract.status === 'SIGNED' ? (
        <div className="notice-panel"><span className="material-symbols-outlined">sync</span><p>Đã đủ chữ ký. Hệ thống đang khóa tiền; trạng thái được cập nhật mỗi 3 giây.</p></div>
      ) : null}

      <section className="detail-grid">
        <div className="panel-card">
          <div className="panel-card__header"><span className="eyebrow">Điều khoản hiện tại</span><h3>Thông tin hợp đồng</h3></div>
          <dl className="detail-list detail-list--large">
            <div><dt>Số lượng</dt><dd>{contract.terms.quantity.value} {contract.terms.quantity.unit}</dd></div>
            <div><dt>Giá</dt><dd>{formatMoney(contract.terms.agreedPrice.amount, contract.terms.agreedPrice.currency)}</dd></div>
            <div><dt>Hạn giao</dt><dd>{formatDate(contract.terms.deliveryDeadline)}</dd></div>
            <div><dt>Cọc bên bán</dt><dd>{formatPercent(contract.terms.sellerDepositRate)}</dd></div>
            <div><dt>Phạt bên mua</dt><dd>{formatPercent(contract.terms.buyerPenaltyRate)}</dd></div>
            <div><dt>Chất lượng</dt><dd>{contract.terms.qualitySpec}</dd></div>
          </dl>
          <ContractRevisionSummary contract={contract} />
          {contract.cancelReason ? <p className="notice-panel notice-panel--danger">{contract.cancelReason}</p> : null}
          <ContractActionsBar contract={contract} />
        </div>

        <div className="panel-card">
          <div className="panel-card__header"><span className="eyebrow">Thương lượng</span><h3>Đàm phán điều khoản</h3><p>Mỗi đề nghị mới tạo một phiên bản điều khoản và yêu cầu các bên ký lại.</p></div>

          <div className="negotiation-history-panel">
            <h3>Lịch sử đề nghị</h3>
            <ContractNegotiationHistory
              contract={contract}
              history={negotiationHistoryQuery.data}
              isLoading={negotiationHistoryQuery.isLoading}
              isError={negotiationHistoryQuery.isError}
            />
          </div>

          {canNegotiate ? (
            <div className="negotiation-form">
              <h3>Gửi điều khoản mới</h3>
              <div className="form-grid">
                <label><span>Số lượng</span><input type="number" min="0.001" step="0.001" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
                <label><span>Giá</span><input type="number" min="0.01" step="0.01" value={price} onChange={(event) => setPrice(event.target.value)} /></label>
                <label><span>Hạn giao</span><input type="date" value={deliveryDeadline} onChange={(event) => setDeliveryDeadline(event.target.value)} /></label>
                <label className="form-grid__full"><span>Quy cách chất lượng</span><textarea value={qualitySpec} onChange={(event) => setQualitySpec(event.target.value)} /></label>
              </div>
              {negotiateMutation.isError ? <p className="notice-panel notice-panel--danger">{getApiErrorMessage(negotiateMutation.error, 'Không gửi được điều khoản mới.')}</p> : null}
              <button className="secondary-button" type="button" disabled={negotiateMutation.isPending} onClick={() => negotiateMutation.mutate()}>Gửi counter-offer</button>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  )
}
