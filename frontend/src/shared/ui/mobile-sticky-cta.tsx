import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"
import { Button } from "@/shared/ui/button"

interface MobileStickyCtaProps {
  summaryLabel: string
  summaryValue: ReactNode
  expanded?: boolean
  onToggle?: () => void
  primaryLabel: string
  onPrimary?: () => void
  secondaryLabel?: string
  onSecondary?: () => void
  className?: string
}

/** Thay sidebar tóm tắt 320px trên mobile — CTA dính đáy, tóm tắt gập được (brief Mobile §13). */
function MobileStickyCta({
  summaryLabel,
  summaryValue,
  expanded,
  onToggle,
  primaryLabel,
  onPrimary,
  secondaryLabel,
  onSecondary,
  className,
}: MobileStickyCtaProps) {
  return (
    <div className={cn("border-t-[0.5px] border-border bg-surface", className)}>
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center justify-between border-b-[0.5px] border-surface-muted px-4 py-2.5 text-[13px] text-text-secondary"
      >
        <span>{summaryLabel}</span>
        <span className="flex items-center gap-1.5 text-text-primary">
          <span className="font-mono font-medium">{summaryValue}</span>
          <span className="ms text-lg text-text-muted">{expanded ? "expand_more" : "expand_less"}</span>
        </span>
      </button>
      <div className="flex gap-2.5 px-4 py-3">
        {secondaryLabel ? (
          <Button variant="outline" onClick={onSecondary} className="h-[46px] rounded-[10px] px-4.5">
            {secondaryLabel}
          </Button>
        ) : null}
        <Button onClick={onPrimary} className="h-[46px] flex-1 rounded-[10px]">
          {primaryLabel}
        </Button>
      </div>
    </div>
  )
}

export { MobileStickyCta }
