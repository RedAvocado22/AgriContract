import { useMemo, useState } from 'react'
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

export function ListingsPage() {
  const userRole = useAuthStore((state) => state.user?.role)
  const normalizedRole = userRole?.trim().toUpperCase()
  const [filters, setFilters] = useState<ListingFilters>(defaultFilters)
  const { data, isLoading, isError } = useListingsQuery(filters)
  const { data: catalogData } = useListingsQuery({ ...defaultFilters, search: '' })

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

  return (
    <div className="page-stack">
      <section className="page-title-row">
        <div>
          <h1>Marketplace listings</h1>
          <p>Find contract-ready agricultural supply with escrow-backed settlement.</p>
        </div>

        {normalizedRole === 'SELLER' ? (
          <Link className="primary-button" to="/listings/create">
            <span className="material-symbols-outlined">add</span>
            Create listing
          </Link>
        ) : null}
      </section>

      <div className="workspace-grid">
        <FilterSidebar
          filters={filters}
          onChange={setFilters}
          categories={categoryOptions}
          priceBounds={priceBounds}
        />

        <section className="content-panel">
          <div className="content-panel__toolbar">
            <label className="search-input">
              <span className="material-symbols-outlined">search</span>
              <input
                value={filters.search ?? ''}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    search: event.target.value,
                  }))
                }
                placeholder="Search product, seller, or category"
              />
            </label>

            <select
              className="sort-select"
              value={filters.sortBy}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  sortBy: event.target.value as ListingFilters['sortBy'],
                }))
              }
            >
              <option value="latest">Newest first</option>
              <option value="price-asc">Price: low to high</option>
              <option value="price-desc">Price: high to low</option>
            </select>
          </div>

          {isLoading ? <div className="empty-state">Loading listings...</div> : null}
          {isError ? <div className="empty-state">Listings could not be loaded.</div> : null}
          {!isLoading && !isError && data?.length === 0 ? (
            <div className="empty-state">No listing matches the current filters.</div>
          ) : null}

          <div className="listing-grid">
            {data?.map((listing) => (
              <ListingCard key={listing.listingId} listing={listing} />
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
