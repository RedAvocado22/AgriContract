import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"
import { SearchInput } from "@/shared/ui/search-input"

interface FilterChip {
  key: string
  label: string
  removable?: boolean
}

interface FilterBarProps {
  searchPlaceholder?: string
  activeFilterCount: number
  onFilterClick?: () => void
  chips?: FilterChip[]
  onChipRemove?: (key: string) => void
  resultsSummary?: ReactNode
  className?: string
}

/** Thay thanh lọc nhiều dropdown trên mobile — gom vào 1 nút mở sheet (brief Mobile §12). */
function FilterBar({
  searchPlaceholder = "Tìm kiếm...",
  activeFilterCount,
  onFilterClick,
  chips,
  onChipRemove,
  resultsSummary,
  className,
}: FilterBarProps) {
  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div className="flex gap-2.5">
        <SearchInput placeholder={searchPlaceholder} className="flex-1" />
        <button
          type="button"
          onClick={onFilterClick}
          className="flex h-9 shrink-0 items-center gap-1.5 rounded-sm border-[0.5px] border-border bg-surface px-3.5 text-sm font-medium text-text-primary"
        >
          <span className="ms text-lg text-text-secondary">tune</span>
          Bộ lọc
          {activeFilterCount > 0 ? (
            <span className="flex size-[18px] items-center justify-center rounded-full bg-primary text-[11px] font-medium text-white">
              {activeFilterCount}
            </span>
          ) : null}
        </button>
      </div>
      {chips?.length ? (
        <div className="flex gap-2 overflow-x-auto">
          {chips.map((chip) => (
            <span
              key={chip.key}
              className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-primary-tint px-3 py-1.5 text-[13px] font-medium text-primary"
            >
              {chip.label}
              {chip.removable ? (
                <button
                  type="button"
                  onClick={() => onChipRemove?.(chip.key)}
                  aria-label={`Xoá ${chip.label}`}
                  className="ms text-sm"
                >
                  close
                </button>
              ) : null}
            </span>
          ))}
        </div>
      ) : null}
      {resultsSummary ? (
        <div className="text-[13px] text-text-secondary">{resultsSummary}</div>
      ) : null}
    </div>
  )
}

export { FilterBar }
export type { FilterChip }
