import type { ComponentProps } from "react"
import { Switch as SwitchPrimitive } from "@base-ui/react/switch"

import { cn } from "@/shared/lib/utils"

type ToggleProps = Omit<ComponentProps<typeof SwitchPrimitive.Root>, "children">

function Toggle({ className, ...props }: ToggleProps) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full bg-border-strong transition-colors data-[checked]:bg-primary",
        className
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="block size-4 translate-x-0.5 rounded-full bg-white transition-transform data-[checked]:translate-x-[18px]" />
    </SwitchPrimitive.Root>
  )
}

export { Toggle }
