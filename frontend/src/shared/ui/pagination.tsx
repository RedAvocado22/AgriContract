import { cn } from "@/shared/lib/utils"
import { IconButton } from "@/shared/ui/icon-button"

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}

function Pagination({ page, totalPages, onPageChange, className }: PaginationProps) {
  return (
    <div className={cn("flex items-center justify-between text-sm text-text-secondary", className)}>
      <span>
        Trang {page} / {totalPages}
      </span>
      <div className="flex items-center gap-1">
        <IconButton
          icon="chevron_left"
          aria-label="Trang trước"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        />
        <IconButton
          icon="chevron_right"
          aria-label="Trang sau"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        />
      </div>
    </div>
  )
}

export { Pagination }
