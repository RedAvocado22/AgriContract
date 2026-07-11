import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"
import { Button } from "@/shared/ui/button"

type EmptyStateVariant = "empty" | "error"

interface EmptyStateProps extends Omit<ComponentProps<"div">, "children"> {
  variant?: EmptyStateVariant
  /** Material Symbols ligature name — ghi đè icon mặc định theo variant. */
  icon?: string
  title: string
  description: string
  actionLabel?: string
  onAction?: () => void
}

const variantConfig: Record<
  EmptyStateVariant,
  { icon: string; iconClassName: string; buttonVariant: "default" | "outline" }
> = {
  empty: { icon: "inbox", iconClassName: "text-border-strong", buttonVariant: "default" },
  error: { icon: "error", iconClassName: "text-danger", buttonVariant: "outline" },
}

function EmptyState({
  variant = "empty",
  icon,
  title,
  description,
  actionLabel,
  onAction,
  className,
  ...props
}: EmptyStateProps) {
  const config = variantConfig[variant]

  return (
    <div
      data-slot="empty-state"
      className={cn(
        "rounded-md border-[0.5px] border-border bg-surface px-5 py-8 text-center",
        className
      )}
      {...props}
    >
      <span className={cn("ms mb-3 inline-block text-[40px]", config.iconClassName)}>
        {icon ?? config.icon}
      </span>
      <div className="mb-1 text-[15px] font-medium text-text-primary">{title}</div>
      <div className="mb-4 text-[13px] text-text-secondary">{description}</div>
      {actionLabel ? (
        <Button variant={config.buttonVariant} size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </div>
  )
}

export { EmptyState }
export type { EmptyStateVariant }
