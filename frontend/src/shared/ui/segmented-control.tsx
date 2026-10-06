import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface SegmentedControlOption {
  key: string
  label: string
}

interface SegmentedControlProps extends Omit<ComponentProps<"div">, "children" | "onChange"> {
  options: SegmentedControlOption[]
  value: string
  onChange?: (key: string) => void
}

function SegmentedControl({ options, value, onChange, className, ...props }: SegmentedControlProps) {
  return (
    <div
      data-slot="segmented-control"
      role="tablist"
      className={cn("inline-flex rounded-sm border-[0.5px] border-border p-0.5", className)}
      {...props}
    >
      {options.map((option) => {
        const isActive = option.key === value

        return (
          <button
            key={option.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange?.(option.key)}
            className={cn(
              "rounded-[6px] px-3.5 py-1.5 text-[13px] font-medium",
              isActive ? "bg-primary text-white" : "text-text-secondary"
            )}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}

export { SegmentedControl }
export type { SegmentedControlOption }
