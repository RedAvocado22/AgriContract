import type { ComponentProps, ReactNode } from "react"
import { Checkbox as CheckboxPrimitive } from "@base-ui/react/checkbox"

import { cn } from "@/shared/lib/utils"

interface CheckboxProps
  extends Omit<ComponentProps<typeof CheckboxPrimitive.Root>, "children"> {
  label?: ReactNode
}

function Checkbox({ label, className, ...props }: CheckboxProps) {
  return (
    <label className="inline-flex items-center gap-2 text-sm text-text-primary">
      <CheckboxPrimitive.Root
        className={cn(
          "flex size-4 shrink-0 items-center justify-center rounded-[4px] border-[1.5px] border-border-strong data-[checked]:border-primary data-[checked]:bg-primary",
          className
        )}
        {...props}
      >
        <CheckboxPrimitive.Indicator className="flex text-white">
          <span className="ms text-[13px]">check</span>
        </CheckboxPrimitive.Indicator>
      </CheckboxPrimitive.Root>
      {label}
    </label>
  )
}

export { Checkbox }
