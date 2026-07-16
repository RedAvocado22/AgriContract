import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'

import { escrowApi } from '../api/escrowApi'
import { useContractQuery } from '../hooks/useContractQuery'
import { useEscrowByContractQuery } from '../hooks/useEscrowQuery'
import { getApiErrorMessage } from '../utils/apiError'
import { formatMoney } from '../utils/formatters'

export function AdminArbitratePage() {
  const { contractId = '' } = useParams()
  const queryClient = useQueryClient()
  const contractQuery = useContractQuery(contractId)
  const escrowQuery = useEscrowByContractQuery(contractId)
  const [buyerAmount, setBuyerAmount] = useState('')
  const [sellerAmount, setSellerAmount] = useState('')
  const [justification, setJustification] = useState('')

  useEffect(() => {
    if (!escrowQuery.data) return
    const total = escrowQuery.data.totalAmount + escrowQuery.data.sellerDeposit
    setBuyerAmount(String(escrowQuery.data.totalAmount))
    setSellerAmount(String(total - escrowQuery.data.totalAmount))
  }, [escrowQuery.data])

  const mutation = useMutation({
    mutationFn: async () => {
      const buyer = Number(buyerAmount)
      const seller = Number(sellerAmount)
      const expected = (escrowQuery.data?.totalAmount ?? 0) + (escrowQuery.data?.sellerDeposit ?? 0)
      if (buyer < 0 || seller < 0 || Math.abs(buyer + seller - expected) > 0.001) throw new Error(`Tổng phân bổ phải bằng ${expected}.`)
      if (!justification.trim()) throw new Error('Vui lòng nhập căn cứ phân xử.')
      return escrowApi.arbitrate(contractId, { buyerAmount: buyer, sellerAmount: seller, justification: justification.trim() })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escrow', contractId] }),
        queryClient.invalidateQueries({ queryKey: ['escrow-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['contract', contractId] }),
      ])
    },
  })

  if (!contractId) return <Navigate to="/dashboard" replace />
  const contract = contractQuery.data
  const escrow = escrowQuery.data
  const currency = escrow?.currency ?? 'VND'
  const total = escrow ? escrow.totalAmount + escrow.sellerDeposit : 0

  return (
    <div className="page-stack">
      <Link className="inline-link" to={`/contracts/${contractId}`}>← Xem hợp đồng</Link>
      <section className="page-title-row"><div><h1>Phân xử tranh chấp</h1><p>{contract?.productName ?? contractId}</p></div></section>
      {contractQuery.isLoading || escrowQuery.isLoading ? <div className="empty-state">Đang tải hồ sơ tranh chấp...</div> : null}
      {contract && contract.status !== 'DISPUTED' ? <div className="notice-panel notice-panel--danger"><p>Hợp đồng chưa ở trạng thái tranh chấp.</p></div> : null}
      {escrow ? (
        <section className="listing-form">
          <div className="offer-summary"><div><span>Tổng cần phân bổ</span><strong>{formatMoney(total, currency)}</strong></div><div><span>Tiền hợp đồng</span><strong>{formatMoney(escrow.totalAmount, currency)}</strong></div><div><span>Cọc bên bán</span><strong>{formatMoney(escrow.sellerDeposit, currency)}</strong></div></div>
          <div className="form-grid">
            <label><span>Hoàn cho bên mua</span><input type="number" min="0" value={buyerAmount} onChange={(event) => setBuyerAmount(event.target.value)} /></label>
            <label><span>Trả cho bên bán</span><input type="number" min="0" value={sellerAmount} onChange={(event) => setSellerAmount(event.target.value)} /></label>
            <label className="form-grid__full"><span>Căn cứ phân xử</span><textarea rows={5} value={justification} onChange={(event) => setJustification(event.target.value)} /></label>
          </div>
          {mutation.isError ? <div className="notice-panel notice-panel--danger" role="alert"><p>{getApiErrorMessage(mutation.error, 'Không thể phân xử tranh chấp.')}</p></div> : null}
          {mutation.isSuccess ? <div className="notice-panel"><p>Đã ghi nhận kết quả phân xử.</p></div> : null}
          <button className="danger-button" type="button" disabled={mutation.isPending || mutation.isSuccess || contract?.status !== 'DISPUTED'} onClick={() => mutation.mutate()}>Xác nhận phân xử</button>
        </section>
      ) : null}
    </div>
  )
}
