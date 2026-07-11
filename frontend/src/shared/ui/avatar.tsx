import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface AvatarProps extends Omit<ComponentProps<"span">, "children"> {
  src?: string
  /** Dùng lấy chữ cái đầu khi không có ảnh, vd "HTX Ea Kar" -> "HE". */
  name?: string
  size?: number
}

function getInitials(name: string) {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ""
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ""
  return (first + last).toUpperCase()
}

function Avatar({ src, name, size = 36, className, ...props }: AvatarProps) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface-muted font-medium text-text-secondary",
        className
      )}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4) }}
      {...props}
    >
      {src ? (
        <img src={src} alt="" className="size-full object-cover" />
      ) : name ? (
        getInitials(name)
      ) : null}
    </span>
  )
}

export { Avatar }
