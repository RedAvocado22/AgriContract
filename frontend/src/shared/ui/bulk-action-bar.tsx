import { cn } from "@/shared/lib/utils"

interface BulkActionBarAction {
  key: string
  label: string
  onClick?: () => void
  /** Nổi bật màu primary — thường là hành động chính (vd "Xử lý lại"). */
  emphasis?: boolean
}

interface BulkActionBarProps {
  selectedCount: number
  actions: BulkActionBarAction[]
  className?: string
}

/** Trượt lên từ đáy khi có hàng được chọn trong bảng bulk-select (brief Mobile §18). */
function BulkActionBar({ selectedCount, actions, className }: BulkActionBarProps) {
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 bg-text-primary px-4 py-3.5 text-white",
        className
      )}
    >
      <span className="text-sm">{selectedCount} đã chọn</span>
      <div className="flex gap-2">
        {actions.map((action) => (
          <button
            key={action.key}
            type="button"
            onClick={action.onClick}
            className={cn(
              "h-[38px] rounded-md px-3.5 text-[13.5px] font-medium",
              action.emphasis ? "bg-primary" : "bg-white/15"
            )}
          >
            {action.label}
          </button>
        ))}
      </div>
    </div>
  )
}

export { BulkActionBar }
export type { BulkActionBarAction }
