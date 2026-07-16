import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { contractApi } from '../../api/contractApi'
import { useAuthStore } from '../../stores/authStore'
import { useContractInteractionStore } from '../../stores/contractInteractionStore'
import type { Contract } from '../../types/contract'
import { getApiErrorMessage } from '../../utils/apiError'
import { getCurrentTermsSignatureState } from '../../utils/contractSignatures'

interface ContractActionsBarProps {
  contract: Contract
}

export function ContractActionsBar({ contract }: ContractActionsBarProps) {
  const queryClient = useQueryClient()
  const user = useAuthStore((state) => state.user)
  const [reason, setReason] = useState('')
  const signedTermsByContractId = useContractInteractionStore((state) => state.signedTermsByContractId)
  const markSigned = useContractInteractionStore((state) => state.markSigned)
  const normalizedRole = user?.role?.trim().toUpperCase()
  const isBuyer = normalizedRole === 'BUYER' && user?.id === contract.buyerId
  const isSeller = normalizedRole === 'SELLER' && user?.id === contract.sellerId
  const isParticipant = isBuyer || isSeller
  const termsFingerprint = JSON.stringify(contract.terms)
  const backendSignatureState = getCurrentTermsSignatureState(contract, user?.id)
  const hasSigned = backendSignatureState
    ?? signedTermsByContractId.get(contract.contractId) === termsFingerprint
  const canSign = isParticipant && ['OFFERED', 'NEGOTIATING'].includes(contract.status) && !hasSigned
  const canConfirmDelivery = isBuyer && contract.status === 'ACTIVE'
  const canCancel = isParticipant && contract.status === 'ACTIVE'
  const canDispute = isBuyer && contract.status === 'DELIVERED'

  const actionMutation = useMutation({
    mutationFn: async (action: 'sign' | 'confirm' | 'cancel' | 'dispute') => {
      if (action === 'sign') return contractApi.sign(contract.contractId)
      if (action === 'confirm') return contractApi.confirmDelivery(contract.contractId)
      if (!reason.trim()) throw new Error('Vui lòng nhập lý do trước khi tiếp tục.')
      if (action === 'cancel') return contractApi.cancel(contract.contractId, reason.trim())
      return contractApi.dispute(contract.contractId, reason.trim())
    },
    onSuccess: async (_result, action) => {
      if (action === 'sign') markSigned(contract.contractId, termsFingerprint)
      setReason('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['contracts'] }),
        queryClient.invalidateQueries({ queryKey: ['contract', contract.contractId] }),
        queryClient.invalidateQueries({ queryKey: ['contract-negotiations', contract.contractId] }),
        queryClient.invalidateQueries({ queryKey: ['escrow', contract.contractId] }),
      ])
    },
    onError: (error, action) => {
      if (action === 'sign' && getApiErrorMessage(error, '').toLowerCase().includes('already signed')) {
        markSigned(contract.contractId, termsFingerprint)
      }
    },
  })

  if (!isParticipant) return null

  return (
    <div className="contract-actions-bar">
      {hasSigned && ['OFFERED', 'NEGOTIATING'].includes(contract.status) ? (
        <div className="notice-panel">
          <span className="material-symbols-outlined">verified</span>
          <p>Bạn đã ký. Đang chờ bên còn lại ký hoặc gửi điều khoản mới.</p>
        </div>
      ) : null}

      {(canCancel || canDispute) ? (
        <label className="compact-field">
          <span>{canDispute ? 'Lý do tranh chấp' : 'Lý do hủy hợp đồng'}</span>
          <textarea value={reason} onChange={(event) => setReason(event.target.value)} />
        </label>
      ) : null}

      {actionMutation.isError ? (
        <div className="notice-panel notice-panel--danger" role="alert">
          <span className="material-symbols-outlined">error</span>
          <p>{getApiErrorMessage(actionMutation.error, 'Không thực hiện được thao tác.')}</p>
        </div>
      ) : null}

      <div className="form-actions">
        {canSign ? (
          <button type="button" className="primary-button" disabled={actionMutation.isPending} onClick={() => actionMutation.mutate('sign')}>
            Ký điều khoản hiện tại
          </button>
        ) : null}
        {canConfirmDelivery ? (
          <button type="button" className="primary-button" disabled={actionMutation.isPending} onClick={() => actionMutation.mutate('confirm')}>
            Xác nhận đã nhận hàng
          </button>
        ) : null}
        {canCancel ? (
          <button type="button" className="secondary-button" disabled={actionMutation.isPending} onClick={() => actionMutation.mutate('cancel')}>
            Hủy hợp đồng
          </button>
        ) : null}
        {canDispute ? (
          <button type="button" className="danger-button" disabled={actionMutation.isPending} onClick={() => actionMutation.mutate('dispute')}>
            Mở tranh chấp
          </button>
        ) : null}
      </div>
    </div>
  )
}
