import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface SystemFreezeAlertProps extends Omit<ComponentProps<"div">, "children"> {
  message?: string
}

/**
 * Ngoại lệ duy nhất trong hệ thống được dùng màu đặc (không phải tint nhạt) —
 * cố ý nổi bật tối đa, dính đỉnh trang, không có nút đóng. Xem brief §6.6.
 */
function SystemFreezeAlert({
  message = "Hệ thống tạm dừng mọi giao dịch tiền. Các thao tác khác vẫn hoạt động.",
  className,
  ...props
}: SystemFreezeAlertProps) {
  return (
    <div
      data-slot="system-freeze-alert"
      role="alert"
      className={cn("rounded-md bg-danger px-5 py-3.5 text-sm text-white", className)}
      {...props}
    >
      {message}
    </div>
  )
}

export { SystemFreezeAlert }
