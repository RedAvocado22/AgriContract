import { cn } from "@/shared/lib/utils"

interface MobileHeaderCrumb {
  label: string
  /** Hiện dạng chip mono "…" — dùng cho các cấp giữa bị gộp lại. */
  collapsed?: boolean
  mono?: boolean
}

interface MobileHeaderProps {
  title: string
  onBack?: () => void
  crumbs?: MobileHeaderCrumb[]
  className?: string
}

/** Thay Breadcrumb trên mobile — chuỗi 3 cấp rút thành nút Quay lại + dải rút gọn (brief Mobile §11). */
function MobileHeader({ title, onBack, crumbs, className }: MobileHeaderProps) {
  return (
    <div className={cn("flex flex-col bg-surface", className)}>
      <div className="flex h-14 items-center gap-2 border-b-[0.5px] border-border px-2.5">
        <button
          type="button"
          onClick={onBack}
          aria-label="Quay lại"
          className="flex size-10 shrink-0 items-center justify-center rounded-[10px] text-text-primary"
        >
          <span className="ms text-[26px]">arrow_back_ios_new</span>
        </button>
        <div className="min-w-0 flex-1 truncate text-base font-medium text-text-primary">{title}</div>
      </div>
      {crumbs?.length ? (
        <div className="flex items-center gap-2 border-b-[0.5px] border-surface-muted px-4 py-3 text-[13px] text-text-secondary">
          {crumbs.map((crumb, index) => (
            <span key={crumb.label} className="flex items-center gap-2">
              {index > 0 ? <span className="text-border-strong">/</span> : null}
              <span
                className={cn(
                  crumb.collapsed && "rounded-md bg-surface-muted px-2 py-0.5 text-text-secondary",
                  crumb.mono && "font-mono",
                  index === crumbs.length - 1 && "text-text-primary"
                )}
              >
                {crumb.label}
              </span>
            </span>
          ))}
        </div>
      ) : null}
    </div>
  )
}

export { MobileHeader }
export type { MobileHeaderCrumb }
