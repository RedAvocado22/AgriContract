import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

type DeadlinePillStatus = "due-soon" | "overdue" | "on-time"

interface DeadlinePillProps extends Omit<ComponentProps<"span">, "children"> {
  status: DeadlinePillStatus
  /** Số ngày còn lại / quá hạn. Bỏ qua khi status = "on-time". */
  days?: number
}

const statusClassName: Record<DeadlinePillStatus, string> = {
  "due-soon": "bg-warning-tint text-warning",
  overdue: "bg-danger-tint text-danger",
  "on-time": "bg-success-tint text-success",
}

function formatLabel(status: DeadlinePillStatus, days?: number) {
  switch (status) {
    case "due-soon":
      return `Còn ${days ?? 0} ngày`
    case "overdue":
      return `Quá hạn ${days ?? 0} ngày`
    case "on-time":
      return "Đúng hạn"
  }
}

function DeadlinePill({ status, days, className, ...props }: DeadlinePillProps) {
  return (
    <span
      data-slot="deadline-pill"
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-[13px] font-medium leading-none whitespace-nowrap",
        statusClassName[status],
        className
      )}
      {...props}
    >
      {formatLabel(status, days)}
    </span>
  )
}

export { DeadlinePill }
export type { DeadlinePillStatus }
