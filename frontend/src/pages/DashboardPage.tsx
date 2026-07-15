import { Link } from 'react-router-dom'

import { useContractsQuery } from '../hooks/useContractsQuery'
import { useListingsQuery } from '../hooks/useListingsQuery'
import { useAuthStore } from '../stores/authStore'
import { formatMoney } from '../utils/formatters'

const listingFilters = {
  categories: [],
  deliveryWindow: 'all',
  search: '',
  sortBy: 'latest' as const,
}

export function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const role = user?.role?.trim().toUpperCase()
  const { data: listings } = useListingsQuery(listingFilters)
  const { data: contracts } = useContractsQuery(role === 'SELLER' ? 'SELLER' : 'BUYER')
  const openListings = listings?.filter((listing) => listing.status === 'ACTIVE').length ?? 0
  const contractItems = contracts ?? []
  const pendingContracts =
    contractItems.filter((contract) => ['OFFERED', 'NEGOTIATING', 'SIGNED'].includes(contract.status)).length
  const escrowEstimate = contractItems.reduce((total, contract) => {
    if (contract.status !== 'ACTIVE' && contract.status !== 'SIGNED') {
      return total
    }

    const quantity = Number(contract.terms?.quantity?.value ?? 0)
    const price = Number(contract.terms?.agreedPrice?.amount ?? 0)
    return total + quantity * price
  }, 0)

  return (
    <div className="dashboard-page">
      <section className="page-title-row">
        <div>
          <h1>Tổng quan</h1>
          <p>Xin chào {user?.name ?? 'bạn'}, đây là tình hình giao dịch hôm nay.</p>
        </div>

        <Link className="primary-button" to="/listings">
          <span className="material-symbols-outlined">list_alt</span>
          Xem tin hàng
        </Link>
      </section>

      <section className="stat-grid">
        <article className="stat-card">
          <span>Tin hàng đang mở</span>
          <strong>{openListings}</strong>
          <small>Có thể tạo đề nghị hợp đồng</small>
        </article>
        <article className="stat-card">
          <span>Hợp đồng cần xử lý</span>
          <strong>{pendingContracts}</strong>
          <small>Đề nghị, chữ ký hoặc ký quỹ</small>
        </article>
        <article className="stat-card">
          <span>Giá trị ký quỹ ước tính</span>
          <strong>{formatMoney(escrowEstimate)}</strong>
          <small>Dựa trên hợp đồng đã ký và đang thực hiện</small>
        </article>
      </section>

      <section className="panel-card">
        <h3>Quy trình</h3>
        <div className="activity-list">
          <div>
            <span className="material-symbols-outlined">storefront</span>
            <p>Xem nguồn hàng đang mở và tạo đề nghị hợp đồng từ trang chi tiết tin hàng.</p>
            <small>Chợ nông sản</small>
          </div>
          <div>
            <span className="material-symbols-outlined">description</span>
            <p>Ký, hủy, xác nhận giao hàng hoặc báo tranh chấp trên màn hình hợp đồng.</p>
            <small>Hợp đồng</small>
          </div>
          <div>
            <span className="material-symbols-outlined">account_balance_wallet</span>
            <p>Theo dõi tiền bên mua và cọc bên bán đang được giữ theo từng hợp đồng.</p>
            <small>Ký quỹ</small>
          </div>
        </div>
      </section>
    </div>
  )
}
