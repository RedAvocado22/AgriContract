import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'

import { contractApi } from '../api/contractApi'
import { useListingQuery } from '../hooks/useListingQuery'
import { useAuthStore } from '../stores/authStore'
import { formatDate, formatMoney } from '../utils/formatters'

const sanitizeIntegerInput = (value: string) => value.split(/[.,]/)[0].replace(/[^\d]/g, '')

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
  const [selectedImageIndex, setSelectedImageIndex] = useState(0)

  useEffect(() => {
    setSelectedImageIndex(0)
  }, [listingId])

  const createContractMutation = useMutation({
    mutationFn: () => {
      if (!data) {
        throw new Error('Thiếu tin hàng')
      }

      const offerPriceFloor = data.currency === 'VND' ? Math.ceil(data.priceFloor) : data.priceFloor

      return contractApi.create({
        listingId: data.listingId,
        terms: {
          quantity: {
            value: Math.max(1, Math.round(Number(quantity || data.quantity))),
            unit: data.quantityUnit,
          },
          agreedPrice: {
            amount: Number(price || offerPriceFloor),
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
    return <div className="empty-state">Đang tải chi tiết tin hàng...</div>
  }

  if (isError || !data) {
    return <div className="empty-state">Không tìm thấy tin hàng.</div>
  }

  const listingImages = data.imageUrls?.length ? data.imageUrls : [data.imageUrl]
  const selectedImage = listingImages[selectedImageIndex] ?? listingImages[0]
  const hasMultipleImages = listingImages.length > 1
  const availableQuantity = Math.max(1, Math.round(data.quantity))
  const canCreateOffer = isAuthenticated && normalizedRole !== 'SELLER' && data.status === 'ACTIVE'
  const priceInputMin = data.currency === 'VND' ? Math.ceil(data.priceFloor) : data.priceFloor
  const priceInputStep = data.currency === 'VND' ? '1' : '0.01'
  const offerQuantity = Math.max(1, Math.round(Number(quantity || availableQuantity)))
  const offerPrice = Number(price || priceInputMin)
  const offerTotal = offerQuantity * offerPrice
  const sellerDeposit = offerTotal * 0.1
  const buyerPenalty = offerTotal * 0.02
  const daysUntilDelivery = Math.max(
    0,
    Math.ceil(
      (new Date(data.deliveryDeadline).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
    ),
  )
  const statusLabel =
    data.status === 'ACTIVE' ? 'Đang nhận đề nghị' : data.status === 'CLOSED' ? 'Đã đóng' : 'Hết hạn'

  const showPreviousImage = () => {
    setSelectedImageIndex((current) => (current === 0 ? listingImages.length - 1 : current - 1))
  }

  const showNextImage = () => {
    setSelectedImageIndex((current) => (current + 1) % listingImages.length)
  }

  return (
    <div className="detail-page">
      <Link className="inline-link" to="/listings">
        <span className="material-symbols-outlined">chevron_left</span>
        Quay lại danh sách tin
      </Link>

      <section className="detail-hero">
        <div className="detail-hero__copy">
          <span className={`status-badge status-badge--${data.status.toLowerCase()}`}>
            {statusLabel}
          </span>
          <h1>{data.productName}</h1>
          <p>{data.description}</p>

          <div className="detail-meta-row">
            <span>{data.category || 'Khác'}</span>
            <span>{data.location || 'Chưa xác nhận địa điểm'}</span>
          </div>

          <div className="detail-price-strip">
            <div>
              <span>Giá sàn</span>
              <strong>{formatMoney(data.priceFloor, data.currency)}</strong>
            </div>
            <div>
              <span>Số lượng còn bán</span>
              <strong>
                {availableQuantity} {data.quantityUnit}
              </strong>
            </div>
            <div>
              <span>Giá trị ước tính</span>
              <strong>{formatMoney(availableQuantity * data.priceFloor, data.currency)}</strong>
            </div>
          </div>

          <div className="detail-trust-row">
            <span>
              <span className="material-symbols-outlined">verified_user</span>
              Hồ sơ bên bán đã xác minh
            </span>
            <span>
              <span className="material-symbols-outlined">account_balance_wallet</span>
              Thanh toán có ký quỹ
            </span>
            <span>
              <span className="material-symbols-outlined">contract</span>
              Sẵn sàng tạo hợp đồng
            </span>
          </div>
        </div>

        <div className="detail-media">
          <div className="detail-gallery">
            <img src={selectedImage} alt={data.productName} />
            {hasMultipleImages ? (
              <>
                <button
                  aria-label="Xem ảnh trước"
                  className="detail-gallery__nav detail-gallery__nav--prev"
                  type="button"
                  onClick={showPreviousImage}
                >
                  <span className="material-symbols-outlined">chevron_left</span>
                </button>
                <button
                  aria-label="Xem ảnh tiếp theo"
                  className="detail-gallery__nav detail-gallery__nav--next"
                  type="button"
                  onClick={showNextImage}
                >
                  <span className="material-symbols-outlined">chevron_right</span>
                </button>
              </>
            ) : null}
          </div>

          {hasMultipleImages ? (
            <div className="detail-gallery__thumbs" aria-label="Danh sách ảnh sản phẩm">
              {listingImages.map((imageUrl, index) => (
                <button
                  className={index === selectedImageIndex ? 'active' : ''}
                  key={`${imageUrl}-${index}`}
                  type="button"
                  onClick={() => setSelectedImageIndex(index)}
                >
                  <img src={imageUrl} alt={`Ảnh sản phẩm ${index + 1}`} />
                </button>
              ))}
            </div>
          ) : null}
        </div>
      </section>

      <section className="detail-grid">
        <div className="panel-card">
          <div className="panel-card__header">
            <span className="eyebrow">Điều khoản mua bán</span>
            <h3>Thông tin tin hàng</h3>
          </div>
          <dl className="detail-list detail-list--large">
            <div>
              <dt>Loại hàng</dt>
              <dd>{data.category || 'Khác'}</dd>
            </div>
            <div>
              <dt>Số lượng</dt>
              <dd>
                {availableQuantity} {data.quantityUnit}
              </dd>
            </div>
            <div>
              <dt>Giá sàn</dt>
              <dd>{formatMoney(data.priceFloor, data.currency)}</dd>
            </div>
            <div>
              <dt>Tổng ước tính</dt>
              <dd>{formatMoney(availableQuantity * data.priceFloor, data.currency)}</dd>
            </div>
            <div>
              <dt>Hạn giao hàng</dt>
              <dd>{formatDate(data.deliveryDeadline)}</dd>
            </div>
            <div>
              <dt>Còn lại</dt>
              <dd>{daysUntilDelivery} ngày</dd>
            </div>
          </dl>
        </div>

        <div className="panel-card">
          <div className="panel-card__header">
            <span className="eyebrow">Bảo đảm giao dịch</span>
            <h3>Ký quỹ và chất lượng</h3>
          </div>
          <div className="assurance-list">
            <div>
              <span className="material-symbols-outlined">fact_check</span>
              <div>
                <strong>Quy cách chất lượng</strong>
                <p>{data.qualityNotes || 'Hai bên có thể chốt quy cách chất lượng trong đề nghị hợp đồng.'}</p>
              </div>
            </div>
            <div>
              <span className="material-symbols-outlined">lock</span>
              <div>
                <strong>Bảo vệ bằng ký quỹ</strong>
                <p>Tiền bên mua và cọc bên bán được theo dõi sau khi hai bên ký.</p>
              </div>
            </div>
            <div>
              <span className="material-symbols-outlined">gavel</span>
              <div>
                <strong>Quy trình tranh chấp</strong>
                <p>Lý do hủy hoặc tranh chấp được ghi nhận trực tiếp trên hợp đồng.</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="detail-action-grid">
        <div className="panel-card seller-panel">
          <div className="panel-card__header">
            <span className="eyebrow">Đối tác</span>
            <h3>Hồ sơ bên bán</h3>
          </div>
          <div className="seller-profile">
            <div className="seller-profile__mark">
              {(data.sellerName || 'S').slice(0, 1).toUpperCase()}
            </div>
            <div>
              <strong>{data.sellerName || 'Bên bán đã xác minh'}</strong>
              <p>{data.location || 'Chưa xác nhận địa điểm'}</p>
            </div>
          </div>
          <dl className="detail-list">
            <div>
              <dt>Mã bên bán</dt>
              <dd>{data.sellerId.slice(0, 8)}</dd>
            </div>
            <div>
              <dt>Xác minh</dt>
              <dd>Đã xác minh</dd>
            </div>
            <div>
              <dt>Trạng thái tin</dt>
              <dd>{statusLabel}</dd>
            </div>
          </dl>
        </div>

        <section className="listing-form offer-panel">
          <div className="panel-card__header">
            <span className="eyebrow">Thao tác bên mua</span>
            <h3>Tạo đề nghị hợp đồng</h3>
            <p>Bắt đầu từ giá sàn, điều chỉnh số lượng hoặc giá rồi gửi đề nghị để chốt điều khoản.</p>
          </div>

          <div className="offer-summary">
            <div>
              <span>Giá trị đề nghị</span>
              <strong>{formatMoney(offerTotal, data.currency)}</strong>
            </div>
            <div>
              <span>Cọc bên bán</span>
              <strong>{formatMoney(sellerDeposit, data.currency)}</strong>
            </div>
            <div>
              <span>Dự phòng phạt bên mua</span>
              <strong>{formatMoney(buyerPenalty, data.currency)}</strong>
            </div>
          </div>

          {canCreateOffer ? (
            <>
              <div className="form-grid offer-form-grid">
                <label>
                  <span>Số lượng ({data.quantityUnit})</span>
                  <input
                    value={quantity}
                    min="1"
                    max={availableQuantity}
                    step="1"
                    type="number"
                    inputMode="numeric"
                    onChange={(event) => setQuantity(sanitizeIntegerInput(event.target.value))}
                    placeholder={`${availableQuantity}`}
                  />
                </label>
                <label>
                  <span>Giá đề nghị ({data.currency})</span>
                  <input
                    value={price}
                    min={priceInputMin}
                    step={priceInputStep}
                    type="number"
                    onChange={(event) => setPrice(event.target.value)}
                    placeholder={`${priceInputMin}`}
                  />
                </label>
                <label className="form-grid__full">
                  <span>Quy cách chất lượng</span>
                  <textarea
                    value={qualitySpec}
                    onChange={(event) => setQualitySpec(event.target.value)}
                    rows={4}
                    placeholder={data.qualityNotes}
                  />
                </label>
              </div>

              {createContractMutation.isError ? (
                <div className="notice-panel notice-panel--danger" role="alert">
                  <span className="material-symbols-outlined">error</span>
                  <p>Không tạo được đề nghị hợp đồng. Vui lòng kiểm tra dịch vụ hợp đồng và thử lại.</p>
                </div>
              ) : null}

              <div className="form-actions">
                <button
                  className="primary-button"
                  type="button"
                  disabled={createContractMutation.isPending}
                  onClick={() => createContractMutation.mutate()}
                >
                  <span className="material-symbols-outlined">contract_edit</span>
                  {createContractMutation.isPending ? 'Đang tạo đề nghị...' : 'Tạo đề nghị'}
                </button>
              </div>
            </>
          ) : !isAuthenticated ? (
            <Link className="primary-button primary-button--full" to="/login">
              <span className="material-symbols-outlined">login</span>
              Đăng nhập để tạo đề nghị
            </Link>
          ) : normalizedRole === 'SELLER' ? (
            <div className="notice-panel">
              <span className="material-symbols-outlined">storefront</span>
              <p>Tài khoản bên bán dùng để đăng và theo dõi tin hàng. Tài khoản bên mua mới tạo đề nghị hợp đồng.</p>
            </div>
          ) : (
            <div className="notice-panel">
              <span className="material-symbols-outlined">block</span>
              <p>Tin hàng này hiện không nhận thêm đề nghị.</p>
            </div>
          )}
        </section>
      </section>
    </div>
  )
}
