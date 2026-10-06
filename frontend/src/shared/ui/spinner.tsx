import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface SpinnerProps extends ComponentProps<"span"> {
  size?: number
}

function Spinner({ size = 16, className, ...props }: SpinnerProps) {
  return (
    <span
      data-slot="spinner"
      role="status"
      aria-label="Đang xử lý"
      className={cn(
        "inline-block animate-spin rounded-full border-2 border-primary-tint border-t-primary",
        className
      )}
      style={{ width: size, height: size }}
      {...props}
    />
  )
}

export { Spinner }
