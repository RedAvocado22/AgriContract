import type { ReactNode } from "react"
import { Tooltip as TooltipPrimitive } from "@base-ui/react/tooltip"

import { cn } from "@/shared/lib/utils"

interface TooltipProps {
  content: ReactNode
  children: ReactNode
  className?: string
}

/**
 * Trigger render ra <button> thật (base-ui) — mở bằng hover lẫn focus,
 * nên tap trên mobile cũng mở được, đúng yêu cầu brief §6.5.
 */
function Tooltip({ content, children, className }: TooltipProps) {
  return (
    <TooltipPrimitive.Root>
      <TooltipPrimitive.Trigger
        className="inline-flex items-center justify-center rounded-full text-text-muted outline-none hover:text-text-secondary"
      >
        {children}
      </TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Positioner sideOffset={6}>
          <TooltipPrimitive.Popup
            className={cn(
              "max-w-64 rounded-sm bg-text-primary px-2.5 py-1.5 text-xs leading-relaxed text-white",
              className
            )}
          >
            {content}
          </TooltipPrimitive.Popup>
        </TooltipPrimitive.Positioner>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  )
}

export { Tooltip }
