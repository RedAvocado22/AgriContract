import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

type MoneySign = "positive" | "negative" | "neutral"
type MoneyEmphasis = "default" | "large"

interface MoneyDisplayProps extends Omit<ComponentProps<"span">, "children"> {
  /** Giá trị tuyệt đối — dấu +/- được quyết định bởi prop `sign`, không suy ra từ số âm. */
  amount: number
  sign?: MoneySign
  emphasis?: MoneyEmphasis
  unit?: string
}

const signStyles: Record<MoneySign, { prefix: string; className: string }> = {
  positive: { prefix: "+ ", className: "text-success" },
  negative: { prefix: "− ", className: "text-danger" },
  neutral: { prefix: "", className: "text-text-primary" },
}

const emphasisStyles: Record<MoneyEmphasis, string> = {
  default: "text-base font-medium",
  large: "text-2xl font-medium",
}

function MoneyDisplay({
  amount,
  sign = "neutral",
  emphasis = "default",
  unit = "₫",
  className,
  ...props
}: MoneyDisplayProps) {
  const { prefix, className: signClassName } = signStyles[sign]
  const formatted = new Intl.NumberFormat("vi-VN").format(Math.abs(amount))

  return (
    <span
      data-slot="money-display"
      className={cn("font-mono tabular-nums", emphasisStyles[emphasis], signClassName, className)}
      {...props}
    >
      {prefix}
      {formatted} {unit}
    </span>
  )
}

export { MoneyDisplay }
export type { MoneySign, MoneyEmphasis }
