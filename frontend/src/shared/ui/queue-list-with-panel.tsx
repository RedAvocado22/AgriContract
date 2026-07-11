import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface QueueListItem {
  key: string
  title: ReactNode
  subtitle?: ReactNode
  badge?: ReactNode
}

interface QueueListWithPanelProps {
  items: QueueListItem[]
  activeKey: string | null
  onSelect: (key: string) => void
  detail: ReactNode
  className?: string
}

/**
 * Pattern Admin workflow (Duyệt bất khả kháng, Điều phối L2...) — khác
 * split-layout thông thường ở chỗ panel phải sticky + item trái active-highlight.
 * Không dùng chung với layout 2 cột thường (brief §Ghi chú implement).
 */
function QueueListWithPanel({ items, activeKey, onSelect, detail, className }: QueueListWithPanelProps) {
  return (
    <div className={cn("grid grid-cols-[320px_1fr] gap-4", className)}>
      <div className="flex flex-col overflow-hidden rounded-md border-[0.5px] border-border bg-surface">
        {items.map((item, index) => {
          const isActive = item.key === activeKey

          return (
            <button
              key={item.key}
              type="button"
              onClick={() => onSelect(item.key)}
              className={cn(
                "flex flex-col items-start gap-1 border-l-2 px-4 py-3 text-left",
                index > 0 && "border-t-[0.5px] border-t-border",
                isActive
                  ? "border-l-primary bg-primary-tint"
                  : "border-l-transparent hover:bg-surface-muted"
              )}
            >
              <div className={cn("text-sm", isActive ? "font-medium text-primary" : "text-text-primary")}>
                {item.title}
              </div>
              {item.subtitle ? (
                <div className="text-[13px] text-text-secondary">{item.subtitle}</div>
              ) : null}
              {item.badge}
            </button>
          )
        })}
      </div>
      <div className="sticky top-6 self-start rounded-md border-[0.5px] border-border bg-surface p-5">
        {detail}
      </div>
    </div>
  )
}

export { QueueListWithPanel }
export type { QueueListItem }
