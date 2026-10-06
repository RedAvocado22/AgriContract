import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

function Skeleton({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded bg-surface-muted", className)}
      {...props}
    />
  )
}

export { Skeleton }
