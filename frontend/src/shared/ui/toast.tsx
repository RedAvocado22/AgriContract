import type { ReactNode } from "react"
import { Toast as ToastPrimitive } from "@base-ui/react/toast"

import { cn } from "@/shared/lib/utils"

const useToastManager = ToastPrimitive.useToastManager

type ToastVariant = "info" | "warning" | "danger" | "success"

const toastIcon: Record<ToastVariant, string> = {
  info: "info",
  warning: "warning",
  danger: "error",
  success: "check_circle",
}

const toastColor: Record<ToastVariant, string> = {
  info: "text-info",
  warning: "text-warning",
  danger: "text-danger",
  success: "text-success",
}

function ToastList() {
  const { toasts } = useToastManager()

  return (
    <>
      {toasts.map((toast) => {
        const variant = (toast.type as ToastVariant | undefined) ?? "info"

        return (
          <ToastPrimitive.Root
            key={toast.id}
            toast={toast}
            className={cn(
              "flex w-80 items-start gap-2.5 rounded-md border-[0.5px] border-border bg-surface px-4 py-3 shadow-lg transition-all",
              "data-[starting-style]:translate-x-[calc(100%+16px)] data-[ending-style]:translate-x-[calc(100%+16px)]"
            )}
          >
            <span className={cn("ms mt-0.5 text-lg", toastColor[variant])}>{toastIcon[variant]}</span>
            <div className="min-w-0 flex-1">
              <ToastPrimitive.Title className="text-sm font-medium text-text-primary" />
              <ToastPrimitive.Description className="text-[13px] text-text-secondary" />
            </div>
            <ToastPrimitive.Close
              aria-label="Đóng"
              className="ms shrink-0 text-base text-text-muted hover:text-text-secondary"
            >
              close
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        )
      })}
    </>
  )
}

interface ToastProviderProps {
  children: ReactNode
}

/** Mount 1 lần ở app root. timeout mặc định 5000ms khớp brief (tự tắt 5 giây). */
function ToastProvider({ children }: ToastProviderProps) {
  return (
    <ToastPrimitive.Provider>
      {children}
      <ToastPrimitive.Portal>
        <ToastPrimitive.Viewport className="fixed top-4 right-4 z-50 flex flex-col gap-2">
          <ToastList />
        </ToastPrimitive.Viewport>
      </ToastPrimitive.Portal>
    </ToastPrimitive.Provider>
  )
}

export { ToastProvider, useToastManager as useToast }
export type { ToastVariant }
