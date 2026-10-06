import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface StickyFormSummaryItem {
  key?: string
  label: string
  value: ReactNode
}

interface StickyFormSummaryProps {
  title: string
  items: StickyFormSummaryItem[]
  footer?: ReactNode
  className?: string
}

function StickyFormSummary({ title, items, footer, className }: StickyFormSummaryProps) {
  return (
    <div
      className={cn(
        "sticky top-6 self-start rounded-md border-[0.5px] border-border bg-surface p-5",
        className
      )}
    >
      <div className="mb-3 text-sm font-medium text-text-primary">{title}</div>
      <div className="flex flex-col gap-2.5">
        {items.map((item) => (
          <div key={item.key ?? item.label} className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">{item.label}</span>
            <span className="text-text-primary">{item.value}</span>
          </div>
        ))}
      </div>
      {footer ? <div className="mt-4 border-t-[0.5px] border-border pt-4">{footer}</div> : null}
    </div>
  )
}

export { StickyFormSummary }
export type { StickyFormSummaryItem }
