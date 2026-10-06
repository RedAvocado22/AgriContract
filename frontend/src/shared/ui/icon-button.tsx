import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

type IconButtonProps = Omit<ComponentProps<"button">, "aria-label"> & {
  /** Material Symbols ligature name. */
  icon: string
  "aria-label": string
}

function IconButton({ icon, className, ...props }: IconButtonProps) {
  return (
    <button
      type="button"
      data-slot="icon-button"
      className={cn(
        "inline-flex size-9 items-center justify-center rounded-sm border-[0.5px] border-border bg-surface text-text-secondary transition-colors hover:bg-surface-muted",
        className
      )}
      {...props}
    >
      <span className="ms text-lg">{icon}</span>
    </button>
  )
}

export { IconButton }
