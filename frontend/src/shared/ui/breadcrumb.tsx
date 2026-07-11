import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface BreadcrumbItem {
  key?: string
  label: string
  href?: string
  /** Mã HĐ/mã bút toán... hiện bằng JetBrains Mono, tô đậm như mục hiện tại. */
  mono?: boolean
}

interface BreadcrumbProps extends Omit<ComponentProps<"nav">, "children"> {
  items: BreadcrumbItem[]
}

function Breadcrumb({ items, className, ...props }: BreadcrumbProps) {
  return (
    <nav
      data-slot="breadcrumb"
      aria-label="Breadcrumb"
      className={cn("flex items-center text-sm text-text-secondary", className)}
      {...props}
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1
        const isEmphasized = isLast || item.mono

        return (
          <span key={item.key ?? item.label} className="flex items-center">
            {index > 0 ? <span className="mx-1.5 text-border-strong">/</span> : null}
            {item.href && !isLast ? (
              <a
                href={item.href}
                className={cn(
                  "hover:text-text-primary",
                  item.mono && "font-mono text-text-primary"
                )}
              >
                {item.label}
              </a>
            ) : (
              <span className={cn(isEmphasized && "text-text-primary", item.mono && "font-mono")}>
                {item.label}
              </span>
            )}
          </span>
        )
      })}
    </nav>
  )
}

export { Breadcrumb }
export type { BreadcrumbItem }
