import type { ReactNode } from "react"
import { Dialog } from "@base-ui/react/dialog"

import { cn } from "@/shared/lib/utils"

interface BottomSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title?: ReactNode
  description?: ReactNode
  children: ReactNode
  className?: string
}

/** Thay Modal giữa màn trên mobile — neo đáy, có thanh kéo (brief Mobile §9). */
function BottomSheet({ open, onOpenChange, title, description, children, className }: BottomSheetProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 bg-black/45 transition-opacity data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup
          className={cn(
            "fixed inset-x-0 bottom-0 rounded-t-[22px] bg-surface px-5 pt-2.5 pb-6",
            "transition-transform duration-200 data-[ending-style]:translate-y-full data-[starting-style]:translate-y-full",
            className
          )}
        >
          <div className="mx-auto mb-4 h-1 w-9 rounded-full bg-border" />
          {title ? (
            <Dialog.Title className="mb-1.5 text-[17px] font-medium text-text-primary">{title}</Dialog.Title>
          ) : null}
          {description ? (
            <Dialog.Description className="mb-4.5 text-sm leading-relaxed text-text-secondary">
              {description}
            </Dialog.Description>
          ) : null}
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export { BottomSheet }
