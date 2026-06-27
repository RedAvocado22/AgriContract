import { Link } from 'react-router-dom'

import type { Listing } from '../../types/listing'

interface ListingCardProps {
  listing: Listing
}

export function ListingCard({ listing }: ListingCardProps) {
  const isActive = listing.status === 'ACTIVE'

  return (
    <article className={`listing-card ${isActive ? '' : 'listing-card--muted'}`}>
      <div className="listing-card__image-wrap">
        <img src={listing.imageUrl} alt={listing.productName} />
        <span className={`status-badge status-badge--${listing.status.toLowerCase()}`}>
          {isActive ? 'Đang mở' : 'Đã đóng'}
        </span>
      </div>

      <div className="listing-card__body">
        <div>
          <h3>{listing.productName}</h3>
          <p>{listing.description}</p>
        </div>

        <dl className="listing-meta">
          <div>
            <dt>Khối lượng</dt>
            <dd>
              {listing.quantity} {listing.quantityUnit}
            </dd>
          </div>
          <div>
            <dt>Giá sàn</dt>
            <dd className="price-accent">
              {listing.priceFloor.toLocaleString('vi-VN')} {listing.currency}/kg
            </dd>
          </div>
          <div>
            <dt>Giao hàng</dt>
            <dd>{listing.deliveryDeadline}</dd>
          </div>
        </dl>

        <Link className="secondary-button" to={`/listings/${listing.listingId}`}>
          Xem chi tiết
        </Link>
      </div>
    </article>
  )
}
