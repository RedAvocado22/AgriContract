import type { ListingFilters } from '../../types/listing'
import { formatMoney } from '../../utils/formatters'
import { DualRangeSlider } from './DualRangeSlider'

interface FilterSidebarProps {
  filters: ListingFilters
  onChange: (next: ListingFilters) => void
  categories: string[]
  priceBounds: {
    min: number
    max: number
  }
}

const PRICE_STEP = 1000

export function FilterSidebar({ filters, onChange, categories, priceBounds }: FilterSidebarProps) {
  const effectiveMin = filters.minPrice ?? priceBounds.min
  const effectiveMax = filters.maxPrice ?? priceBounds.max
  const hasPriceRange = priceBounds.max > priceBounds.min

  const toggleCategory = (category: string) => {
    const nextCategories = filters.categories.includes(category)
      ? filters.categories.filter((item) => item !== category)
      : [...filters.categories, category]

    onChange({
      ...filters,
      categories: nextCategories,
    })
  }

  return (
    <aside className="filter-panel">
      <div className="panel-card">
        <div className="panel-card__header">
          <h3>Filters</h3>
          <p>Narrow listings by product, price, and delivery timing.</p>
        </div>

        <div className="filter-group">
          <span className="filter-label">Category</span>
          {categories.length === 0 ? (
            <p className="filter-empty-hint">Categories appear when listings load.</p>
          ) : null}
          {categories.map((category) => (
            <label key={category} className="checkbox-row">
              <input
                type="checkbox"
                checked={filters.categories.includes(category)}
                onChange={() => toggleCategory(category)}
              />
              <span>{category}</span>
            </label>
          ))}
        </div>

        <div className="filter-group">
          <div className="filter-label-row">
            <span className="filter-label">Floor price</span>
            {(filters.minPrice !== undefined || filters.maxPrice !== undefined) && (
              <button
                className="filter-reset"
                type="button"
                onClick={() =>
                  onChange({
                    ...filters,
                    minPrice: undefined,
                    maxPrice: undefined,
                  })
                }
              >
                Reset
              </button>
            )}
          </div>
          {hasPriceRange ? (
            <DualRangeSlider
              min={effectiveMin}
              max={effectiveMax}
              limitMin={priceBounds.min}
              limitMax={priceBounds.max}
              step={PRICE_STEP}
              onChange={({ min, max }) =>
                onChange({
                  ...filters,
                  minPrice: min === priceBounds.min ? undefined : min,
                  maxPrice: max === priceBounds.max ? undefined : max,
                })
              }
            />
          ) : (
            <p className="filter-empty-hint">Price range needs at least two listings.</p>
          )}
          <div className="range-row">
            <span>{formatMoney(effectiveMin || 0)}</span>
            <span>{formatMoney(effectiveMax || 0)}</span>
          </div>
        </div>

        <div className="filter-group">
          <span className="filter-label">Delivery window</span>
          <select
            value={filters.deliveryWindow ?? 'all'}
            onChange={(event) =>
              onChange({
                ...filters,
                deliveryWindow: event.target.value,
              })
            }
          >
            <option value="all">All deadlines</option>
            <option value="30d">Within 30 days</option>
            <option value="90d">1 to 3 months</option>
            <option value="365d">Within 12 months</option>
          </select>
        </div>
      </div>
    </aside>
  )
}
