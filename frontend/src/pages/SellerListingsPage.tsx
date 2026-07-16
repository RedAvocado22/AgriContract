import { Link } from 'react-router-dom'

import { ListingCard } from '../components/listings/ListingCard'
import { useSellerListingsQuery } from '../hooks/useSellerListingsQuery'

export function SellerListingsPage() {
  const { data, isLoading, isError, refetch } = useSellerListingsQuery()

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div>
          <h1>Tin hàng của tôi</h1>
          <p>Theo dõi các tin đang mở, đã đóng hoặc hết hạn.</p>
        </div>
        <Link className="primary-button" to="/listings/create">Đăng tin mới</Link>
      </section>

      {isLoading ? <div className="empty-state">Đang tải tin hàng...</div> : null}
      {isError ? (
        <div className="notice-panel notice-panel--danger" role="alert">
          <p>Không tải được tin của seller.</p>
          <button className="secondary-button" type="button" onClick={() => void refetch()}>Thử lại</button>
        </div>
      ) : null}
      {!isLoading && !isError && data?.length === 0 ? <div className="empty-state">Bạn chưa có tin hàng nào.</div> : null}
      <div className="listing-grid">
        {data?.map((listing) => <ListingCard key={listing.listingId} listing={listing} />)}
      </div>
    </div>
  )
}
