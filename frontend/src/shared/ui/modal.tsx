import type { ReactNode } from "react"
import { Dialog } from "@base-ui/react/dialog"

import { cn } from "@/shared/lib/utils"

type ModalSize = "sm" | "md" | "lg"

const sizeClassName: Record<ModalSize, string> = {
  sm: "w-[400px]",
  md: "w-[560px]",
  lg: "w-[720px]",
}

interface ModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  size?: ModalSize
  title: ReactNode
  titleClassName?: string
  children: ReactNode
  footer?: ReactNode
}

function Modal({
  open,
  onOpenChange,
  size = "md",
  title,
  titleClassName,
  children,
  footer,
}: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 bg-black/40" />
        <Dialog.Popup
          className={cn(
            "fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-md border-[0.5px] border-border bg-surface",
            sizeClassName[size]
          )}
        >
          <div className="border-b-[0.5px] border-border px-5 py-4">
            <Dialog.Title className={cn("text-base font-medium text-text-primary", titleClassName)}>
              {title}
            </Dialog.Title>
          </div>
          <div className="p-5 text-sm text-text-secondary">{children}</div>
          {footer ? (
            <div className="flex justify-end gap-2 border-t-[0.5px] border-border px-5 py-4">
              {footer}
            </div>
          ) : null}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export { Modal }
export type { ModalSize }
