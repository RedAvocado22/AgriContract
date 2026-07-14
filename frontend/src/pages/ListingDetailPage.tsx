import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'

import { contractApi } from '../api/contractApi'
import { useListingQuery } from '../hooks/useListingQuery'
import { useAuthStore } from '../stores/authStore'
import { formatDate, formatMoney } from '../utils/formatters'

export function ListingDetailPage() {
  const { listingId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data, isLoading, isError } = useListingQuery(listingId ?? '')
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const userRole = useAuthStore((state) => state.user?.role)
  const normalizedRole = userRole?.trim().toUpperCase()
  const [quantity, setQuantity] = useState('')
  const [price, setPrice] = useState('')
  const [qualitySpec, setQualitySpec] = useState('')

  const createContractMutation = useMutation({
    mutationFn: () => {
      if (!data) {
        throw new Error('Listing is missing')
      }

      return contractApi.create({
        listingId: data.listingId,
        terms: {
          quantity: {
            value: Number(quantity || data.quantity),
            unit: data.quantityUnit,
          },
          agreedPrice: {
            amount: Number(price || data.priceFloor),
            currency: data.currency,
          },
          deliveryDeadline: data.deliveryDeadline,
          buyerPenaltyRate: 0.02,
          sellerDepositRate: 0.1,
          qualitySpec: qualitySpec || data.qualityNotes,
        },
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['contracts'] })
      navigate('/contracts')
    },
  })

  if (!listingId) {
    return <Navigate to="/listings" replace />
  }

  if (isLoading) {
    return <div className="empty-state">Loading listing details...</div>
  }

  if (isError || !data) {
    return <div className="empty-state">Listing not found.</div>
  }

  const canCreateOffer = isAuthenticated && normalizedRole !== 'SELLER' && data.status === 'ACTIVE'

  return (
    <div className="detail-page">
      <Link className="inline-link" to="/listings">
        <span className="material-symbols-outlined">chevron_left</span>
        Back to listings
      </Link>

      <section className="detail-hero">
        <div className="detail-hero__copy">
          <span className={`status-badge status-badge--${data.status.toLowerCase()}`}>
            {data.status === 'ACTIVE' ? 'Open' : data.status === 'CLOSED' ? 'Closed' : 'Expired'}
          </span>
          <h1>{data.productName}</h1>
          <p>{data.description}</p>

          <div className="detail-stats">
            <div>
              <span>Seller</span>
              <strong>{data.sellerName || 'Verified seller'}</strong>
            </div>
            <div>
              <span>Location</span>
              <strong>{data.location || 'To be confirmed'}</strong>
            </div>
            <div>
              <span>Delivery</span>
              <strong>{formatDate(data.deliveryDeadline)}</strong>
            </div>
          </div>
        </div>

        <img src={data.imageUrl} alt={data.productName} />
      </section>

      <section className="detail-grid">
        <div className="panel-card">
          <h3>Listing terms</h3>
          <dl className="detail-list">
            <div>
              <dt>Category</dt>
              <dd>{data.category || 'Other'}</dd>
            </div>
            <div>
              <dt>Quantity</dt>
              <dd>
                {data.quantity} {data.quantityUnit}
              </dd>
            </div>
            <div>
              <dt>Floor price</dt>
              <dd>{formatMoney(data.priceFloor, data.currency)}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{data.status}</dd>
            </div>
          </dl>
        </div>

        <div className="panel-card">
          <h3>Quality specification</h3>
          <p>{data.qualityNotes || 'Quality notes can be finalized in the contract offer.'}</p>
          {!isAuthenticated ? (
            <Link className="primary-button primary-button--inline" to="/login">
              <span className="material-symbols-outlined">login</span>
              Sign in to create an offer
            </Link>
          ) : null}
        </div>
      </section>

      {canCreateOffer ? (
        <section className="listing-form offer-panel">
          <div className="panel-card__header">
            <span className="eyebrow">Buyer action</span>
            <h3>Create contract offer</h3>
            <p>Start from the listing floor terms and adjust what you want to offer.</p>
          </div>

          <div className="form-grid">
            <label>
              <span>Quantity ({data.quantityUnit})</span>
              <input
                value={quantity}
                min="0.001"
                max={data.quantity}
                step="0.001"
                type="number"
                onChange={(event) => setQuantity(event.target.value)}
                placeholder={`${data.quantity}`}
              />
            </label>
            <label>
              <span>Agreed price ({data.currency})</span>
              <input
                value={price}
                min={data.priceFloor}
                step="1000"
                type="number"
                onChange={(event) => setPrice(event.target.value)}
                placeholder={`${data.priceFloor}`}
              />
            </label>
            <label className="form-grid__full">
              <span>Quality specification</span>
              <textarea
                value={qualitySpec}
                onChange={(event) => setQualitySpec(event.target.value)}
                rows={4}
                placeholder={data.qualityNotes}
              />
            </label>
          </div>

          <div className="form-actions">
            <button
              className="primary-button"
              type="button"
              disabled={createContractMutation.isPending}
              onClick={() => createContractMutation.mutate()}
            >
              {createContractMutation.isPending ? 'Creating offer...' : 'Create offer'}
            </button>
          </div>
        </section>
      ) : null}
    </div>
  )
}
