import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface TabItem {
  key: string
  label: string
}

interface TabsUnderlineProps extends Omit<ComponentProps<"div">, "children" | "onChange"> {
  tabs: TabItem[]
  value: string
  onChange?: (key: string) => void
  /** Cuộn ngang thay vì wrap — dùng khi nhiều tab không vừa 1 dòng trên mobile (brief Mobile §7). */
  scrollable?: boolean
}

function TabsUnderline({ tabs, value, onChange, scrollable, className, ...props }: TabsUnderlineProps) {
  return (
    <div
      data-slot="tabs-underline"
      role="tablist"
      className={cn(
        "flex gap-6 border-b-[0.5px] border-border",
        scrollable && "overflow-x-auto",
        className
      )}
      {...props}
    >
      {tabs.map((tab) => {
        const isActive = tab.key === value

        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange?.(tab.key)}
            className={cn(
              "-mb-px border-b-2 pb-2.5 text-sm",
              scrollable && "shrink-0 whitespace-nowrap",
              isActive
                ? "border-primary font-medium text-primary"
                : "border-transparent text-text-secondary hover:text-text-primary"
            )}
          >
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}

export { TabsUnderline }
export type { TabItem }
