import type { ListingFilters } from '../../types/listing'
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
          <h3>Bộ lọc</h3>
          <p>Lọc tin theo loại hàng, giá sàn và thời gian giao.</p>
        </div>

        <div className="filter-group">
          <span className="filter-label">Loại hàng</span>
          {categories.length === 0 ? (
            <p className="filter-empty-hint">Loại hàng sẽ hiện sau khi tải danh sách.</p>
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
            <span className="filter-label">Giá sàn</span>
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
                Đặt lại
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
            <p className="filter-empty-hint">Cần ít nhất hai tin để lọc theo khoảng giá.</p>
          )}
        </div>

        <div className="filter-group">
          <span className="filter-label">Thời hạn giao</span>
          <select
            value={filters.deliveryWindow ?? 'all'}
            onChange={(event) =>
              onChange({
                ...filters,
                deliveryWindow: event.target.value,
              })
            }
          >
            <option value="all">Tất cả thời hạn</option>
            <option value="30d">Trong 30 ngày</option>
            <option value="90d">Từ 1 đến 3 tháng</option>
            <option value="365d">Trong 12 tháng</option>
          </select>
        </div>
      </div>
    </aside>
  )
}
