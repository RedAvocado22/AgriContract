import type { ListingFilters } from '../../types/listing'

interface FilterSidebarProps {
  filters: ListingFilters
  onChange: (next: ListingFilters) => void
}

const categories = ['Cà phê', 'Cao su', 'Lúa gạo', 'Điều']

export function FilterSidebar({ filters, onChange }: FilterSidebarProps) {
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
          <span className="filter-label">Mức giá</span>
          <label className="price-input">
            <span>Tối đa</span>
            <input
              type="number"
              min="20000"
              max="150000"
              step="5000"
              value={filters.maxPrice ?? 150000}
              onChange={(event) =>
                onChange({
                  ...filters,
                  maxPrice: Number(event.target.value),
                })
              }
            />
          </label>
          <input
            className="price-slider"
            type="range"
            min="20000"
            max="150000"
            step="5000"
            value={filters.maxPrice ?? 150000}
            onChange={(event) =>
              onChange({
                ...filters,
                maxPrice: Number(event.target.value),
              })
            }
          />
          <div className="range-row">
            <span>20.000 VND</span>
            <span>{(filters.maxPrice ?? 150000).toLocaleString('vi-VN')} VND</span>
          </div>
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
