import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface ProgressBarSegment {
  key?: string
  /** Phần trăm 0–100. Nhiều segment = thanh xếp chồng; 1 segment = thanh đơn sắc. */
  value: number
  /** Tailwind bg-* class, vd "bg-primary". */
  color: string
}

interface ProgressBarProps extends Omit<ComponentProps<"div">, "children"> {
  segments: ProgressBarSegment[]
}

function ProgressBar({ segments, className, ...props }: ProgressBarProps) {
  return (
    <div
      data-slot="progress-bar"
      className={cn("flex h-2 overflow-hidden rounded-full bg-surface-muted", className)}
      {...props}
    >
      {segments.map((segment, index) => (
        <div
          key={segment.key ?? index}
          className={segment.color}
          style={{ width: `${segment.value}%` }}
        />
      ))}
    </div>
  )
}

export { ProgressBar }
export type { ProgressBarSegment }
