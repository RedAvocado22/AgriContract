import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface ReputationDonutProps extends Omit<ComponentProps<"div">, "children"> {
  /** 0–100. */
  score: number
  /** Nhãn định tính, vd "Uy tín tốt". */
  label?: string
  size?: number
}

function ReputationDonut({ score, label, size = 64, className, ...props }: ReputationDonutProps) {
  const clamped = Math.min(100, Math.max(0, score))
  const innerSize = size - 20

  return (
    <div
      data-slot="reputation-donut"
      className={cn("inline-flex items-center gap-4", className)}
      {...props}
    >
      <div
        className="flex shrink-0 items-center justify-center rounded-full"
        style={{
          width: size,
          height: size,
          background: `conic-gradient(var(--color-primary) 0% ${clamped}%, var(--color-surface-muted) ${clamped}% 100%)`,
        }}
      >
        <div
          className="flex items-center justify-center rounded-full bg-surface text-sm font-medium text-text-primary"
          style={{ width: innerSize, height: innerSize }}
        >
          {score}
        </div>
      </div>
      {label ? <div className="text-[13px] font-medium text-primary">{label}</div> : null}
    </div>
  )
}

export { ReputationDonut }
