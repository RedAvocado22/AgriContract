import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface TextareaProps extends Omit<ComponentProps<"textarea">, "id"> {
  id?: string
  label?: ReactNode
  required?: boolean
  error?: string
  containerClassName?: string
}

function Textarea({
  id,
  label,
  required,
  error,
  containerClassName,
  className,
  ...props
}: TextareaProps) {
  return (
    <div className={cn("flex flex-col", containerClassName)}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 text-[13px] text-text-primary">
          {label} {required ? <span className="text-danger">*</span> : null}
        </label>
      ) : null}
      <textarea
        id={id}
        className={cn(
          "min-h-16 rounded-sm border-[0.5px] bg-surface px-3 py-2.5 text-sm text-text-primary outline-none placeholder:text-text-muted focus:ring-2",
          error
            ? "border-danger focus:ring-danger/20"
            : "border-border focus:border-primary focus:ring-primary/20",
          className
        )}
        {...props}
      />
      {error ? <div className="mt-1 text-[13px] text-danger">{error}</div> : null}
    </div>
  )
}

export { Textarea }
