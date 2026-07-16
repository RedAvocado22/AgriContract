import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { FilterSidebar } from '../components/listings/FilterSidebar'
import { ListingCard } from '../components/listings/ListingCard'
import { useListingsQuery } from '../hooks/useListingsQuery'
import { useAuthStore } from '../stores/authStore'
import type { ListingFilters } from '../types/listing'

const defaultFilters: ListingFilters = {
  categories: [],
  minPrice: undefined,
  maxPrice: undefined,
  deliveryWindow: 'all',
  search: '',
  sortBy: 'latest',
}

const PAGE_SIZE = 12

export function ListingsPage() {
  const userRole = useAuthStore((state) => state.user?.role)
  const normalizedRole = userRole?.trim().toUpperCase()
  const [filters, setFilters] = useState<ListingFilters>(defaultFilters)
  const [searchInput, setSearchInput] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading, isError } = useListingsQuery(filters)
  const { data: catalogData } = useListingsQuery({ ...defaultFilters, search: '' })

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setFilters((current) => ({ ...current, search: searchInput }))
      setPage(0)
    }, 350)
    return () => window.clearTimeout(timeoutId)
  }, [searchInput])

  const categoryOptions = useMemo(
    () =>
      Array.from(new Set((catalogData ?? []).map((listing) => listing.category).filter(Boolean))).sort(
        (a, b) => a.localeCompare(b),
      ),
    [catalogData],
  )

  const priceBounds = useMemo(() => {
    const prices = (catalogData ?? []).map((listing) => listing.priceFloor)

    if (prices.length === 0) {
      return {
        min: 0,
        max: 0,
      }
    }

    return {
      min: Math.min(...prices),
      max: Math.max(...prices),
    }
  }, [catalogData])

  const pageCount = Math.max(1, Math.ceil((data?.length ?? 0) / PAGE_SIZE))
  const pageItems = (data ?? []).slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div>
          <h1>Chợ nông sản</h1>
          <p>Tìm nguồn hàng nông sản sẵn sàng ký hợp đồng, có ký quỹ bảo chứng.</p>
        </div>

        {normalizedRole === 'SELLER' ? (
          <Link className="primary-button" to="/listings/create">
            <span className="material-symbols-outlined">add</span>
            Đăng tin hàng
          </Link>
        ) : null}
      </section>

      <div className="workspace-grid">
        <FilterSidebar
          filters={filters}
          onChange={(nextFilters) => {
            setFilters(nextFilters)
            setPage(0)
          }}
          categories={categoryOptions}
          priceBounds={priceBounds}
        />

        <section className="content-panel">
          <div className="content-panel__toolbar">
            <label className="search-input">
              <span className="material-symbols-outlined">search</span>
              <input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Tìm sản phẩm, bên bán hoặc loại hàng"
              />
            </label>

            <select
              className="sort-select"
              value={filters.sortBy}
              onChange={(event) => {
                setPage(0)
                setFilters((current) => ({
                  ...current,
                  sortBy: event.target.value as ListingFilters['sortBy'],
                }))
              }}
            >
              <option value="latest">Mới nhất trước</option>
              <option value="price-asc">Giá: thấp đến cao</option>
              <option value="price-desc">Giá: cao đến thấp</option>
            </select>
          </div>

          {isLoading ? <div className="empty-state">Đang tải tin hàng...</div> : null}
          {isError ? <div className="empty-state">Không tải được danh sách tin hàng.</div> : null}
          {!isLoading && !isError && data?.length === 0 ? (
            <div className="empty-state">Không có tin hàng phù hợp với bộ lọc hiện tại.</div>
          ) : null}

          <div className="listing-grid">
            {pageItems.map((listing) => (
              <ListingCard key={listing.listingId} listing={listing} />
            ))}
          </div>

          {(data?.length ?? 0) > PAGE_SIZE ? (
            <nav className="pagination" aria-label="Phân trang tin hàng">
              <button type="button" className="secondary-button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Trang trước</button>
              <span>Trang {page + 1}/{pageCount}</span>
              <button type="button" className="secondary-button" disabled={page + 1 >= pageCount} onClick={() => setPage((current) => current + 1)}>Trang sau</button>
            </nav>
          ) : null}
        </section>
      </div>
    </div>
  )
}
