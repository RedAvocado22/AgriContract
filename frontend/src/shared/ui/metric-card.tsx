import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface MetricCardProps extends Omit<ComponentProps<"div">, "children"> {
  label: string
  value: ReactNode
  valueClassName?: string
}

function MetricCard({ label, value, valueClassName, className, ...props }: MetricCardProps) {
  return (
    <div
      data-slot="metric-card"
      className={cn("rounded-md bg-surface-muted px-5 py-4", className)}
      {...props}
    >
      <div className="mb-2 text-[13px] text-text-secondary">{label}</div>
      <div className={cn("text-2xl font-medium text-text-primary", valueClassName)}>{value}</div>
    </div>
  )
}

export { MetricCard }
