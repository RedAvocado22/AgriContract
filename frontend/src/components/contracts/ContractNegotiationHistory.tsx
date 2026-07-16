import type { Contract, NegotiationHistory } from '../../types/contract'
import { getCurrentTermsSignatureState, hasAuthoritativeSignatureState } from '../../utils/contractSignatures'
import { formatDate, formatDateTime, formatMoney } from '../../utils/formatters'

interface ContractRevisionSummaryProps {
  contract: Contract
}

export function ContractRevisionSummary({ contract }: ContractRevisionSummaryProps) {
  const hasSignatureState = hasAuthoritativeSignatureState(contract)
  if (!hasSignatureState && contract.termsRevision === undefined) return null

  const buyerSigned = getCurrentTermsSignatureState(contract, contract.buyerId) ?? false
  const sellerSigned = getCurrentTermsSignatureState(contract, contract.sellerId) ?? false

  return (
    <div className="signature-summary" aria-label="Trạng thái chữ ký điều khoản hiện tại">
      <div>
        <span>Phiên bản điều khoản</span>
        <strong>{contract.termsRevision ?? 'Chưa đánh số'}</strong>
      </div>
      {hasSignatureState ? (
        <>
          <div>
            <span>Bên mua</span>
            <strong className={buyerSigned ? 'signature-state signature-state--signed' : 'signature-state'}>
              {buyerSigned ? 'Đã ký' : 'Chờ ký'}
            </strong>
          </div>
          <div>
            <span>Bên bán</span>
            <strong className={sellerSigned ? 'signature-state signature-state--signed' : 'signature-state'}>
              {sellerSigned ? 'Đã ký' : 'Chờ ký'}
            </strong>
          </div>
        </>
      ) : null}
    </div>
  )
}

interface ContractNegotiationHistoryProps {
  contract: Contract
  history?: NegotiationHistory
  isLoading: boolean
  isError: boolean
}

const proposerLabel = (contract: Contract, proposedBy: string) => {
  if (proposedBy === contract.buyerId) return `Bên mua · ${contract.buyerOrgName}`
  if (proposedBy === contract.sellerId) return `Bên bán · ${contract.sellerOrgName}`
  return proposedBy
}

export function ContractNegotiationHistory({
  contract,
  history,
  isLoading,
  isError,
}: ContractNegotiationHistoryProps) {
  if (isLoading) return <p className="panel-empty-copy">Đang tải lịch sử đàm phán...</p>
  if (isError) return <p className="panel-empty-copy">Không tải được lịch sử đàm phán.</p>
  if (!history?.supported) {
    return <p className="panel-empty-copy">Lịch sử đàm phán chưa khả dụng trên máy chủ hiện tại.</p>
  }
  if (history.entries.length === 0) {
    return <p className="panel-empty-copy">Chưa có đề nghị nào được ghi nhận.</p>
  }

  const entries = [...history.entries].sort((left, right) => right.termsRevision - left.termsRevision)

  return (
    <ol className="negotiation-history">
      {entries.map((entry) => (
        <li key={`${entry.termsRevision}-${entry.proposedAt ?? entry.proposedBy}`}>
          <div className="negotiation-history__heading">
            <div>
              <strong>Phiên bản {entry.termsRevision}</strong>
              <span>{proposerLabel(contract, entry.proposedBy)}</span>
            </div>
            <time>{formatDateTime(entry.proposedAt)}</time>
          </div>
          <dl className="negotiation-history__terms">
            <div><dt>Số lượng</dt><dd>{entry.terms.quantity.value} {entry.terms.quantity.unit}</dd></div>
            <div><dt>Giá</dt><dd>{formatMoney(entry.terms.agreedPrice.amount, entry.terms.agreedPrice.currency)}</dd></div>
            <div><dt>Hạn giao</dt><dd>{formatDate(entry.terms.deliveryDeadline)}</dd></div>
          </dl>
          <p>{entry.terms.qualitySpec}</p>
          {entry.signatures !== undefined ? (
            <small>{entry.signatures.length}/2 bên đã ký phiên bản này</small>
          ) : null}
        </li>
      ))}
    </ol>
  )
}
