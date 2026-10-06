import type { ComponentProps, ReactNode } from "react"
import { RadioGroup as RadioGroupPrimitive } from "@base-ui/react/radio-group"
import { Radio as RadioPrimitive } from "@base-ui/react/radio"

import { cn } from "@/shared/lib/utils"

type RadioGroupProps = ComponentProps<typeof RadioGroupPrimitive>

function RadioGroup({ className, ...props }: RadioGroupProps) {
  return <RadioGroupPrimitive className={cn("flex flex-col gap-2.5", className)} {...props} />
}

interface RadioButtonProps extends Omit<ComponentProps<typeof RadioPrimitive.Root>, "children"> {
  label?: ReactNode
}

function RadioButton({ label, className, ...props }: RadioButtonProps) {
  return (
    <label className="inline-flex items-center gap-2 text-sm text-text-primary">
      <RadioPrimitive.Root
        className={cn(
          "flex size-4 shrink-0 items-center justify-center rounded-full border-[1.5px] border-border-strong data-[checked]:border-primary",
          className
        )}
        {...props}
      >
        <RadioPrimitive.Indicator className="size-2 rounded-full bg-primary" />
      </RadioPrimitive.Root>
      {label}
    </label>
  )
}

export { RadioGroup, RadioButton }
