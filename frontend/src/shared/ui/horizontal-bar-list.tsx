import { cn } from "@/shared/lib/utils"

interface HorizontalBarItem {
  key?: string
  label: string
  /** % chiều rộng thanh, 0-100 (đã tính theo scale max ở feature layer). */
  value: number
  displayValue: string
  warning?: boolean
}

interface HorizontalBarListProps {
  items: HorizontalBarItem[]
  /** vd ["0%","3%","6%","9%"] — hàng mốc dưới cùng. */
  scaleLabels?: string[]
  className?: string
}

/** Thay biểu đồ cột dọc trên mobile — nhãn không bị xoay/cắt chữ (brief Mobile §4). */
function HorizontalBarList({ items, scaleLabels, className }: HorizontalBarListProps) {
  return (
    <div className={cn("flex flex-col gap-3.5", className)}>
      {items.map((item) => (
        <div key={item.key ?? item.label}>
          <div className="mb-1.5 flex items-center justify-between text-[13px]">
            <span className="whitespace-nowrap">{item.label}</span>
            <span
              className={cn(
                "font-mono font-medium",
                item.warning ? "text-warning" : "text-text-primary"
              )}
            >
              {item.displayValue}
            </span>
          </div>
          <div className="h-3 overflow-hidden rounded-full bg-surface-muted">
            <div
              className={cn("h-full rounded-full", item.warning ? "bg-warning" : "bg-primary")}
              style={{ width: `${item.value}%` }}
            />
          </div>
        </div>
      ))}
      {scaleLabels ? (
        <div className="mt-1 flex justify-between font-mono text-[11px] text-text-muted">
          {scaleLabels.map((label) => (
            <span key={label}>{label}</span>
          ))}
        </div>
      ) : null}
    </div>
  )
}

export { HorizontalBarList }
export type { HorizontalBarItem }
