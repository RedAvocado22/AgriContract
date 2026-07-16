import { Link } from 'react-router-dom'

import type { Listing } from '../../types/listing'
import { formatDate, formatMoney } from '../../utils/formatters'

interface ListingCardProps {
  listing: Listing
}

export function ListingCard({ listing }: ListingCardProps) {
  const isActive = listing.status === 'ACTIVE'
  const displayQuantity = listing.quantity

  return (
    <article className={`listing-card ${isActive ? '' : 'listing-card--muted'}`}>
      <div className="listing-card__image-wrap">
        <img src={listing.imageUrl} alt={listing.productName} loading="lazy" decoding="async" />
        <span className={`status-badge status-badge--${listing.status.toLowerCase()}`}>
          {isActive ? 'Đang mở' : listing.status === 'CLOSED' ? 'Đã đóng' : 'Hết hạn'}
        </span>
      </div>

      <div className="listing-card__body">
        <div className="listing-card__heading">
          <span className="eyebrow">{listing.category}</span>
          <h3>{listing.productName}</h3>
          <p>{listing.description}</p>
        </div>

        <dl className="listing-meta">
          <div>
            <dt>Số lượng</dt>
            <dd>
              {displayQuantity} {listing.quantityUnit}
            </dd>
          </div>
          <div>
            <dt>Giá sàn</dt>
            <dd className="price-accent">{formatMoney(listing.priceFloor, listing.currency)}</dd>
          </div>
          <div>
            <dt>Giao hàng</dt>
            <dd>{formatDate(listing.deliveryDeadline)}</dd>
          </div>
        </dl>

        <Link className="secondary-button" to={`/listings/${listing.listingId}`}>
          Xem chi tiết
        </Link>
      </div>
    </article>
  )
}
