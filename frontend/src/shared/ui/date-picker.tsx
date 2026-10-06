import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface DatePickerProps extends Omit<ComponentProps<"input">, "id" | "type"> {
  id?: string
  label?: ReactNode
  required?: boolean
  error?: string
  containerClassName?: string
}

/**
 * Native <input type="date"> — base-ui chưa có date-picker primitive.
 * Icon lịch chỉ trang trí, click bất kỳ đâu trong field đều mở được picker của trình duyệt.
 */
function DatePicker({
  id,
  label,
  required,
  error,
  containerClassName,
  className,
  ...props
}: DatePickerProps) {
  return (
    <div className={cn("flex flex-col", containerClassName)}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 text-[13px] text-text-primary">
          {label} {required ? <span className="text-danger">*</span> : null}
        </label>
      ) : null}
      <div
        className={cn(
          "flex items-center justify-between rounded-sm border-[0.5px] bg-surface px-3 py-2.5 focus-within:ring-2",
          error
            ? "border-danger focus-within:ring-danger/20"
            : "border-border focus-within:border-primary focus-within:ring-primary/20"
        )}
      >
        <input
          id={id}
          type="date"
          className={cn(
            "w-full bg-transparent text-sm text-text-primary outline-none [&::-webkit-calendar-picker-indicator]:hidden",
            className
          )}
          {...props}
        />
        <span className="ms shrink-0 pl-2 text-lg text-text-muted">calendar_today</span>
      </div>
      {error ? <div className="mt-1 text-[13px] text-danger">{error}</div> : null}
    </div>
  )
}

export { DatePicker }
