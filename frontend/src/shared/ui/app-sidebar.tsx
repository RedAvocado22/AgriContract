import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface SidebarItem {
  key?: string
  label: string
  /** Material Symbols ligature name. */
  icon: string
  href?: string
  active?: boolean
  /** Nhóm mục theo section (dùng cho Admin sidebar, phân nhóm). Bỏ trống = danh sách phẳng. */
  section?: string
}

interface AppSidebarProps extends Omit<ComponentProps<"nav">, "children"> {
  items: SidebarItem[]
}

function AppSidebar({ items, className, ...props }: AppSidebarProps) {
  let lastSection: string | undefined

  return (
    <nav
      data-slot="app-sidebar"
      className={cn(
        "flex w-60 shrink-0 flex-col gap-1 rounded-md border-[0.5px] border-border bg-surface py-4",
        className
      )}
      {...props}
    >
      {items.map((item) => {
        const showSectionHeader = item.section !== undefined && item.section !== lastSection
        lastSection = item.section

        return (
          <div key={item.key ?? item.label}>
            {showSectionHeader ? (
              <div className="px-4 pt-3 pb-1.5 text-[11px] font-medium tracking-wide text-text-muted uppercase">
                {item.section}
              </div>
            ) : null}
            <a
              href={item.href ?? "#"}
              aria-current={item.active ? "page" : undefined}
              className={cn(
                "flex items-center gap-2.5 border-l-2 px-4 py-2.5 text-sm",
                item.active
                  ? "border-primary bg-primary-tint font-medium text-primary"
                  : "border-transparent text-text-primary hover:bg-surface-muted"
              )}
            >
              <span className="ms text-lg">{item.icon}</span>
              {item.label}
            </a>
          </div>
        )
      })}
    </nav>
  )
}

export { AppSidebar }
export type { SidebarItem }
