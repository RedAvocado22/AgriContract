import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface DeltaRow {
  key?: string
  /** vd "Chênh lệch cam kết (Delta 1)". */
  label: string
  before: string
  after: string
  /** vd "− 320 kg". */
  delta: string
  /** danger = có thể tính phạt (Delta 1), neutral = không tính phạt (Delta 2). */
  consequence: "danger" | "neutral"
  consequenceLabel: string
}

interface DeltaComparisonBlockProps extends Omit<ComponentProps<"div">, "children"> {
  rows: DeltaRow[]
  /** "cards" = 1 thẻ/dòng, dùng trên mobile khi lưới 4 cột không vừa 390px (brief Mobile §8). */
  layout?: "table" | "cards"
}

/**
 * Delta 1 (cam kết ↔ seller cân) và Delta 2 (seller ↔ buyer cân) không bao giờ gộp —
 * mỗi dòng giữ nhãn/hệ quả riêng, xem brief §3.6.
 */
function DeltaComparisonBlock({ rows, layout = "table", className, ...props }: DeltaComparisonBlockProps) {
  if (layout === "cards") {
    return (
      <div data-slot="delta-comparison-block" className={cn("flex flex-col gap-3", className)} {...props}>
        {rows.map((row) => (
          <div
            key={row.key ?? row.label}
            className="rounded-[14px] border-[0.5px] border-border bg-surface px-4 py-3.5"
          >
            <div className="mb-2.5 flex items-center justify-between">
              <span className="text-sm font-medium text-text-primary">{row.label}</span>
              <span
                className={cn(
                  "font-mono text-base font-medium",
                  row.consequence === "danger" ? "text-danger" : "text-text-secondary"
                )}
              >
                {row.delta}
              </span>
            </div>
            <div className="mb-3 font-mono text-[13px] text-text-secondary">
              {row.before} → {row.after}
            </div>
            <span
              className={cn(
                "inline-flex rounded-full px-2.5 py-1 text-[12.5px] font-medium",
                row.consequence === "danger"
                  ? "bg-danger-tint text-danger"
                  : "bg-neutral-tint text-text-secondary"
              )}
            >
              {row.consequenceLabel}
            </span>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div
      data-slot="delta-comparison-block"
      className={cn(
        "overflow-x-auto rounded-md border-[0.5px] border-border bg-surface px-5 py-4",
        className
      )}
      {...props}
    >
      {rows.map((row, index) => (
        <div
          key={row.key ?? row.label}
          className={cn(
            "grid grid-cols-[1.3fr_1.2fr_0.7fr_1fr] items-center gap-3 py-2 text-sm",
            index < rows.length - 1 && "border-b-[0.5px] border-border"
          )}
        >
          <div className="text-text-primary whitespace-nowrap">{row.label}</div>
          <div className="font-mono text-text-primary whitespace-nowrap">
            {row.before} → {row.after}
          </div>
          <div
            className={cn(
              "font-mono whitespace-nowrap",
              row.consequence === "danger" ? "text-danger" : "text-text-secondary"
            )}
          >
            {row.delta}
          </div>
          <div>
            <span
              className={cn(
                "inline-flex rounded-full px-2 py-1 text-[13px] whitespace-nowrap",
                row.consequence === "danger"
                  ? "bg-danger-tint text-danger"
                  : "bg-neutral-tint text-text-secondary"
              )}
            >
              {row.consequenceLabel}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}

export { DeltaComparisonBlock }
export type { DeltaRow }
