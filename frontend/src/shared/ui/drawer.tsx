import type { ReactNode } from "react"
import { Dialog } from "@base-ui/react/dialog"

import { cn } from "@/shared/lib/utils"

interface DrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  children: ReactNode
  footer?: ReactNode
  width?: number
}

/** Trượt phải, dùng cho chi tiết vùng trồng / chi tiết bút toán (brief §6.5). */
function Drawer({ open, onOpenChange, title, children, footer, width = 420 }: DrawerProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 bg-black/40 transition-opacity data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup
          className={cn(
            "fixed top-0 right-0 flex h-full flex-col border-l-[0.5px] border-border bg-surface",
            "transition-transform duration-200 data-[ending-style]:translate-x-full data-[starting-style]:translate-x-full"
          )}
          style={{ width }}
        >
          <div className="flex items-center justify-between border-b-[0.5px] border-border px-5 py-4">
            <Dialog.Title className="text-base font-medium text-text-primary">{title}</Dialog.Title>
            <Dialog.Close aria-label="Đóng" className="ms text-xl text-text-muted hover:text-text-secondary">
              close
            </Dialog.Close>
          </div>
          <div className="flex-1 overflow-y-auto p-5 text-sm text-text-secondary">{children}</div>
          {footer ? (
            <div className="flex justify-end gap-2 border-t-[0.5px] border-border px-5 py-4">{footer}</div>
          ) : null}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export { Drawer }
