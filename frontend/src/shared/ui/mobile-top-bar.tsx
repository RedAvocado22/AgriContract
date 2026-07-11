import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface MobileTopBarProps {
  title: string
  onMenuClick?: () => void
  hasUnreadNotifications?: boolean
  avatar?: ReactNode
  className?: string
}

/** Thay TopBar trên mobile — nút ☰ mở drawer thay vì search inline (brief Mobile §1). */
function MobileTopBar({ title, onMenuClick, hasUnreadNotifications, avatar, className }: MobileTopBarProps) {
  return (
    <header
      className={cn("flex h-14 items-center gap-3 border-b-[0.5px] border-border bg-surface px-3.5", className)}
    >
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Mở menu"
        className="flex size-10 shrink-0 items-center justify-center rounded-[10px] border-[0.5px] border-border bg-surface text-text-primary"
      >
        <span className="ms text-2xl">menu</span>
      </button>
      <div className="min-w-0 flex-1 truncate text-base font-medium text-text-primary">{title}</div>
      <span className="relative flex shrink-0">
        <span className="ms text-2xl text-text-secondary">notifications</span>
        {hasUnreadNotifications ? (
          <span className="absolute -top-px -right-px size-2 rounded-full border-[1.5px] border-surface bg-danger" />
        ) : null}
      </span>
      {avatar ?? (
        <span className="size-8 shrink-0 rounded-full border-[0.5px] border-border bg-surface-muted" />
      )}
    </header>
  )
}

export { MobileTopBar }
