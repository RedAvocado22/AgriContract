import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface TopBarProps extends Omit<ComponentProps<"header">, "children"> {
  reputationScore: number
  hasUnreadNotifications?: boolean
  searchPlaceholder?: string
  avatar?: ReactNode
}

function TopBar({
  reputationScore,
  hasUnreadNotifications,
  searchPlaceholder = "Tìm hợp đồng, đối tác...",
  avatar,
  className,
  ...props
}: TopBarProps) {
  return (
    <header
      data-slot="top-bar"
      className={cn(
        "flex h-16 items-center gap-4 rounded-md border-[0.5px] border-border bg-surface px-5",
        className
      )}
      {...props}
    >
      <div className="flex h-9 max-w-70 flex-1 items-center rounded-sm border-[0.5px] border-border px-3 text-sm text-text-muted">
        {searchPlaceholder}
      </div>
      <span className="relative flex shrink-0">
        <span className="ms text-[22px] text-text-secondary">notifications</span>
        {hasUnreadNotifications ? (
          <span className="absolute -top-px -right-px size-2 rounded-full border-[1.5px] border-surface bg-danger" />
        ) : null}
      </span>
      <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-primary-tint px-2.5 py-1 text-[13px] font-medium text-primary">
        {reputationScore}
      </span>
      {avatar ?? (
        <span className="size-8 shrink-0 rounded-full border-[0.5px] border-border bg-surface-muted" />
      )}
    </header>
  )
}

export { TopBar }
