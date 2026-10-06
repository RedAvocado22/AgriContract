import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"
import { numberToVietnameseWords } from "@/shared/lib/vietnamese-number"

interface MoneyInputProps {
  id?: string
  label?: ReactNode
  required?: boolean
  value: number
  onChange: (value: number) => void
  unit?: string
  error?: string
  /** Ghi đè dòng "đọc số bằng chữ" mặc định — dùng khi 0 là giá trị hợp lệ cần giải thích riêng. */
  hint?: string
  disabled?: boolean
  className?: string
}

function MoneyInput({
  id,
  label,
  required,
  value,
  onChange,
  unit = "₫",
  error,
  hint,
  disabled,
  className,
}: MoneyInputProps) {
  const formatted = value > 0 ? new Intl.NumberFormat("vi-VN").format(value) : ""

  const handleChange = (raw: string) => {
    const digits = raw.replace(/\D/g, "")
    onChange(digits ? Number(digits) : 0)
  }

  return (
    <div className={cn("flex flex-col", className)}>
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
          value={formatted}
          onChange={(event) => handleChange(event.target.value)}
          disabled={disabled}
          inputMode="numeric"
          placeholder="0"
          className="w-full bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted"
        />
        <span className="shrink-0 pl-2 text-sm text-text-muted">{unit}</span>
      </div>
      {error ? (
        <div className="mt-1 text-[13px] text-danger">{error}</div>
      ) : hint ? (
        <div className="mt-1 text-[11px] text-text-muted">{hint}</div>
      ) : value > 0 ? (
        <div className="mt-1 text-[11px] text-text-muted">{numberToVietnameseWords(value)}</div>
      ) : null}
    </div>
  )
}

export { MoneyInput }
