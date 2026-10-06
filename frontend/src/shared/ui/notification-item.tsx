import { cn } from "@/shared/lib/utils"

type NotificationVariant = "info" | "warning" | "danger" | "success"

interface NotificationItemProps {
  variant: NotificationVariant
  title: string
  description: string
  relativeTime: string
  actionLabel?: string
  onAction?: () => void
  unread?: boolean
  className?: string
}

const variantIcon: Record<NotificationVariant, string> = {
  info: "info",
  warning: "warning",
  danger: "error",
  success: "check_circle",
}

const variantColor: Record<NotificationVariant, string> = {
  info: "text-info",
  warning: "text-warning",
  danger: "text-danger",
  success: "text-success",
}

function NotificationItem({
  variant,
  title,
  description,
  relativeTime,
  actionLabel,
  onAction,
  unread,
  className,
}: NotificationItemProps) {
  return (
    <div className={cn("flex gap-3 px-4 py-3", unread && "bg-primary-tint/40", className)}>
      <span className={cn("ms mt-0.5 text-lg", variantColor[variant])}>{variantIcon[variant]}</span>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium text-text-primary">{title}</div>
        <div className="text-[13px] text-text-secondary">{description}</div>
        <div className="mt-1 flex items-center gap-3">
          <span className="text-[11px] text-text-muted">{relativeTime}</span>
          {actionLabel ? (
            <button
              type="button"
              onClick={onAction}
              className="text-[13px] font-medium text-primary"
            >
              {actionLabel}
            </button>
          ) : null}
        </div>
      </div>
      {unread ? <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden /> : null}
    </div>
  )
}

export { NotificationItem }
export type { NotificationVariant }
