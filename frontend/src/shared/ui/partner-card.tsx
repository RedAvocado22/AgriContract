import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface PartnerCardProps extends Omit<ComponentProps<"div">, "children"> {
  name: string
  /** vd "Hợp tác xã", "Doanh nghiệp". */
  orgType: string
  reputationScore: number
  avatarUrl?: string
}

function PartnerCard({
  name,
  orgType,
  reputationScore,
  avatarUrl,
  className,
  ...props
}: PartnerCardProps) {
  return (
    <div
      data-slot="partner-card"
      className={cn(
        "flex items-center gap-3 rounded-md border-[0.5px] border-border bg-surface px-5 py-4",
        className
      )}
      {...props}
    >
      {avatarUrl ? (
        <img src={avatarUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
      ) : (
        <span className="size-10 shrink-0 rounded-full bg-surface-muted" aria-hidden />
      )}
      <div>
        <div className="text-sm font-medium text-text-primary">{name}</div>
        <div className="text-[13px] text-text-secondary">
          {orgType} · <span className="font-medium text-primary">Uy tín {reputationScore}</span>
        </div>
      </div>
    </div>
  )
}

export { PartnerCard }
