import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface KeyValueItem {
  key?: string
  label: string
  value: ReactNode
}

interface KeyValueListProps extends Omit<ComponentProps<"div">, "children"> {
  items: KeyValueItem[]
}

function KeyValueList({ items, className, ...props }: KeyValueListProps) {
  return (
    <div
      data-slot="key-value-list"
      className={cn("rounded-md border-[0.5px] border-border bg-surface px-5 py-4", className)}
      {...props}
    >
      {items.map((item, index) => (
        <div
          key={item.key ?? item.label}
          className={cn(
            "flex items-center justify-between py-2.5 text-sm",
            index < items.length - 1 && "border-b-[0.5px] border-border"
          )}
        >
          <span className="text-text-secondary">{item.label}</span>
          <span className="text-text-primary">{item.value}</span>
        </div>
      ))}
    </div>
  )
}

export { KeyValueList }
export type { KeyValueItem }
