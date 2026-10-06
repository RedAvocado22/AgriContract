import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface DateRangePickerProps {
  id?: string
  label?: ReactNode
  startValue: string
  endValue: string
  onStartChange: (value: string) => void
  onEndChange: (value: string) => void
  className?: string
}

function DateRangePicker({
  id,
  label,
  startValue,
  endValue,
  onStartChange,
  onEndChange,
  className,
}: DateRangePickerProps) {
  return (
    <div className={cn("flex flex-col", className)}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 text-[13px] text-text-primary">
          {label}
        </label>
      ) : null}
      <div className="flex items-center gap-2">
        <div className="flex flex-1 items-center justify-between rounded-sm border-[0.5px] border-border bg-surface px-3 py-2.5 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20">
          <input
            id={id}
            type="date"
            value={startValue}
            onChange={(event) => onStartChange(event.target.value)}
            className="w-full bg-transparent text-sm text-text-primary outline-none [&::-webkit-calendar-picker-indicator]:hidden"
          />
          <span className="ms shrink-0 pl-2 text-lg text-text-muted">calendar_today</span>
        </div>
        <span className="text-text-muted">–</span>
        <div className="flex flex-1 items-center justify-between rounded-sm border-[0.5px] border-border bg-surface px-3 py-2.5 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20">
          <input
            type="date"
            value={endValue}
            onChange={(event) => onEndChange(event.target.value)}
            min={startValue || undefined}
            className="w-full bg-transparent text-sm text-text-primary outline-none [&::-webkit-calendar-picker-indicator]:hidden"
          />
          <span className="ms shrink-0 pl-2 text-lg text-text-muted">calendar_today</span>
        </div>
      </div>
    </div>
  )
}

export { DateRangePicker }
