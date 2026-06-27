import { Link, Navigate, useParams } from 'react-router-dom'

import { useListingQuery } from '../hooks/useListingQuery'

export function ListingDetailPage() {
  const { listingId } = useParams()
  const { data, isLoading, isError } = useListingQuery(listingId ?? '')

  if (!listingId) {
    return <Navigate to="/listings" replace />
  }

  if (isLoading) {
    return <div className="empty-state">Đang tải chi tiết tin đăng...</div>
  }

  if (isError || !data) {
    return <div className="empty-state">Không tìm thấy tin đăng.</div>
  }

  return (
    <div className="detail-page">
      <Link className="inline-link" to="/listings">
        <span className="material-symbols-outlined">chevron_left</span>
        Quay lại danh sách
      </Link>

      <section className="detail-hero">
        <div className="detail-hero__copy">
          <span className="status-badge status-badge--active">Đang mở</span>
          <h1>{data.productName}</h1>
          <p>{data.description}</p>

          <div className="detail-stats">
            <div>
              <span>Bên bán</span>
              <strong>{data.sellerName}</strong>
            </div>
            <div>
              <span>Khu vực</span>
              <strong>{data.location}</strong>
            </div>
            <div>
              <span>Giao hàng</span>
              <strong>{data.deliveryDeadline}</strong>
            </div>
          </div>
        </div>

        <img src={data.imageUrl} alt={data.productName} />
      </section>

      <section className="detail-grid">
        <div className="panel-card">
          <h3>Thông tin chào giá</h3>
          <dl className="detail-list">
            <div>
              <dt>Danh mục</dt>
              <dd>{data.category}</dd>
            </div>
            <div>
              <dt>Khối lượng</dt>
              <dd>
                {data.quantity} {data.quantityUnit}
              </dd>
            </div>
            <div>
              <dt>Giá sàn</dt>
              <dd>
                {data.priceFloor.toLocaleString('vi-VN')} {data.currency}/kg
              </dd>
            </div>
            <div>
              <dt>Trạng thái</dt>
              <dd>{data.status === 'ACTIVE' ? 'Đang mở' : 'Đã đóng'}</dd>
            </div>
          </dl>
        </div>

        <div className="panel-card">
          <h3>Thông số chất lượng</h3>
          <p>{data.qualityNotes}</p>
          <Link className="primary-button primary-button--inline" to="/contracts">
            <span className="material-symbols-outlined">handshake</span>
            Tạo đề nghị hợp đồng
          </Link>
        </div>
      </section>
    </div>
  )
}
