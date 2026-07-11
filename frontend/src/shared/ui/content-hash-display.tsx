import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface ContentHashDisplayProps extends Omit<ComponentProps<"span">, "children"> {
  hash: string
  /** Rút gọn dạng "a3f9e1d4…c21b7f80". Mặc định true. */
  truncate?: boolean
}

function truncateHash(hash: string) {
  if (hash.length <= 20) return hash
  return `${hash.slice(0, 8)}…${hash.slice(-8)}`
}

function ContentHashDisplay({ hash, truncate = true, className, ...props }: ContentHashDisplayProps) {
  const display = truncate ? truncateHash(hash) : hash

  return (
    <span
      data-slot="content-hash-display"
      title={hash}
      className={cn("inline-flex items-center gap-1.5 font-mono text-sm text-text-primary", className)}
      {...props}
    >
      {display}
      <button
        type="button"
        onClick={() => {
          void navigator.clipboard?.writeText(hash)
        }}
        aria-label="Sao chép hash"
        className="ms cursor-pointer text-base text-text-muted hover:text-text-secondary"
      >
        content_copy
      </button>
    </span>
  )
}

export { ContentHashDisplay }
