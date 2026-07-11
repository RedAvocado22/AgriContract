import { useState, type ComponentProps, type ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface TextInputProps extends Omit<ComponentProps<"input">, "id"> {
  id?: string
  label?: ReactNode
  required?: boolean
  /** Đơn vị dính bên phải, vd "kg", "%". Dùng MoneyInput riêng cho tiền. */
  unit?: string
  /** Icon hoặc nút bổ sung bên phải input. Bị ẩn nếu type="password". */
  suffix?: ReactNode
  error?: string
  hint?: string
  containerClassName?: string
}

function TextInput({
  id,
  label,
  required,
  unit,
  suffix,
  error,
  hint,
  containerClassName,
  className,
  type = "text",
  ...props
}: TextInputProps) {
  const [showPassword, setShowPassword] = useState(false)
  const isPassword = type === "password"

  // Đổi type động nếu là input password
  const inputType = isPassword ? (showPassword ? "text" : "password") : type

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
          type={inputType}
          className={cn(
            "w-full bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted",
            isPassword ? "tracking-[0.15em] placeholder:tracking-normal" : "",
            className
          )}
          {...props}
        />
        {isPassword ? (
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            className="ml-2 shrink-0 text-text-muted hover:text-text-secondary cursor-pointer"
            aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          >
            <span className="ms text-[19px]">
              {showPassword ? "visibility" : "visibility_off"}
            </span>
          </button>
        ) : unit ? (
          <span className="shrink-0 pl-2 text-sm text-text-muted">{unit}</span>
        ) : suffix ? (
          <div className="shrink-0 pl-2">{suffix}</div>
        ) : null}
      </div>
      {error ? (
        <div className="mt-1 text-[13px] text-danger">{error}</div>
      ) : hint ? (
        <div className="mt-1 text-[11px] text-text-muted">{hint}</div>
      ) : null}
    </div>
  )
}

export { TextInput }
export type { TextInputProps }
