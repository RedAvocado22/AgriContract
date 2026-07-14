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
        </div>

        <div className="filter-group">
          <span className="filter-label">Danh mục</span>
          {categories.length === 0 ? (
            <p className="filter-empty-hint">Đang tải danh mục từ dữ liệu thật...</p>
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
            <span className="filter-label">Mức giá</span>
            {(effectiveMin !== priceBounds.min || effectiveMax !== priceBounds.max) && (
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
        </div>

        <div className="filter-group">
          <span className="filter-label">Kỳ hạn giao hàng</span>
          <select
            value={filters.deliveryWindow ?? 'all'}
            onChange={(event) =>
              onChange({
                ...filters,
                deliveryWindow: event.target.value,
              })
            }
          >
            <option value="all">Tất cả</option>
            <option value="30d">Trong 30 ngày</option>
            <option value="90d">1 - 3 tháng</option>
            <option value="365d">Hơn 3 tháng</option>
          </select>
        </div>
      </div>
    </aside>
  )
}
