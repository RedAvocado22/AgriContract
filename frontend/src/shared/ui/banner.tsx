import type { ComponentProps, ReactNode } from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/shared/lib/utils"
import { Button } from "@/shared/ui/button"

const bannerVariants = cva(
  "flex flex-col items-stretch gap-3 rounded-md px-4.5 py-3.5 text-sm sm:flex-row sm:items-center",
  {
  variants: {
    variant: {
      info: "bg-info-tint text-info-strong",
      warning: "bg-warning-tint text-warning-strong",
      danger: "bg-danger-tint text-danger-strong",
      success: "bg-success-tint text-success-strong",
    },
  },
  defaultVariants: {
    variant: "info",
  },
})

const bannerIcon: Record<"info" | "warning" | "danger" | "success", string> = {
  info: "info",
  warning: "warning",
  danger: "error",
  success: "check_circle",
}

interface BannerProps
  extends Omit<ComponentProps<"div">, "children">,
    VariantProps<typeof bannerVariants> {
  children: ReactNode
  actionLabel?: string
  onAction?: () => void
}

function Banner({
  className,
  variant = "info",
  children,
  actionLabel,
  onAction,
  ...props
}: BannerProps) {
  const resolvedVariant = variant ?? "info"

  return (
    <div
      data-slot="banner"
      className={cn(
        bannerVariants({ variant }),
        actionLabel && "sm:justify-between",
        className
      )}
      {...props}
    >
      <span className="flex items-center gap-3">
        <span className="ms text-lg">{bannerIcon[resolvedVariant]}</span>
        {children}
      </span>
      {actionLabel ? (
        <Button
          variant={resolvedVariant === "danger" ? "destructive" : "outline"}
          size="sm"
          onClick={onAction}
          className="w-full shrink-0 sm:w-auto"
        >
          {actionLabel}
        </Button>
      ) : null}
    </div>
  )
}

export { Banner, bannerVariants }
