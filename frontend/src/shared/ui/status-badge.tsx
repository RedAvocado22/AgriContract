import type { ComponentProps } from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/shared/lib/utils"

const statusBadgeVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-1 text-[13px] font-medium leading-none whitespace-nowrap",
  {
    variants: {
      variant: {
        primary: "bg-primary-tint text-primary",
        success: "bg-success-tint text-success",
        warning: "bg-warning-tint text-warning",
        danger: "bg-danger-tint text-danger",
        info: "bg-info-tint text-info",
        neutral: "bg-neutral-tint text-text-secondary",
      },
    },
    defaultVariants: {
      variant: "neutral",
    },
  }
)

interface StatusBadgeProps
  extends ComponentProps<"span">,
    VariantProps<typeof statusBadgeVariants> {}

function StatusBadge({ className, variant, ...props }: StatusBadgeProps) {
  return (
    <span
      data-slot="status-badge"
      className={cn(statusBadgeVariants({ variant, className }))}
      {...props}
    />
  )
}

export { StatusBadge, statusBadgeVariants }
