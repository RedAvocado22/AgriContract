import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

type VerifyChipVariant = "verified" | "unverified"

interface VerifyChipProps extends Omit<ComponentProps<"span">, "children"> {
  variant: VerifyChipVariant
  /** Ghi đè nhãn mặc định nếu cần (vd tên đơn vị xác thực). */
  label?: string
}

const config: Record<
  VerifyChipVariant,
  { className: string; icon: string; defaultLabel: string }
> = {
  verified: {
    className: "bg-primary-tint text-primary",
    icon: "verified_user",
    defaultLabel: "Đã xác thực chữ ký",
  },
  unverified: {
    className: "bg-warning-tint text-warning",
    icon: "warning",
    defaultLabel: "Không xác thực được nguồn",
  },
}

function VerifyChip({ variant, label, className, ...props }: VerifyChipProps) {
  const { className: variantClassName, icon, defaultLabel } = config[variant]

  return (
    <span
      data-slot="verify-chip"
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[13px] font-medium leading-none whitespace-nowrap",
        variantClassName,
        className
      )}
      {...props}
    >
      <span className="ms text-[15px]">{icon}</span>
      {label ?? defaultLabel}
    </span>
  )
}

export { VerifyChip }
export type { VerifyChipVariant }
