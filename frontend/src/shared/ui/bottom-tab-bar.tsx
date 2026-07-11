import { cn } from "@/shared/lib/utils"

interface BottomTabBarItem {
  key: string
  label: string
  /** Material Symbols ligature name. */
  icon: string
  active?: boolean
  href?: string
}

interface BottomTabBarProps {
  items: BottomTabBarItem[]
  className?: string
}

/**
 * Thay hẳn AppSidebar trên mobile (không phải bản responsive-collapse của nó) —
 * 5 mục cố định, dùng bản build mobile riêng theo brief §7 Responsive.
 */
function BottomTabBar({ items, className }: BottomTabBarProps) {
  return (
    <nav
      data-slot="bottom-tab-bar"
      className={cn(
        "flex items-center justify-around border-t-[0.5px] border-border bg-surface py-1.5",
        className
      )}
    >
      {items.map((item) => (
        <a
          key={item.key}
          href={item.href ?? "#"}
          aria-current={item.active ? "page" : undefined}
          className={cn(
            "flex min-w-14 flex-col items-center gap-0.5 rounded-sm px-2 py-1 text-[11px]",
            item.active ? "text-primary" : "text-text-muted"
          )}
        >
          <span className="ms text-xl">{item.icon}</span>
          {item.label}
        </a>
      ))}
    </nav>
  )
}

export { BottomTabBar }
export type { BottomTabBarItem }
