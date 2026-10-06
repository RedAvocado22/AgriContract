import { useEffect, useState, type ReactNode } from "react"

import { Button } from "@/shared/ui/button"
import { Modal } from "@/shared/ui/modal"

interface DangerConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description: ReactNode
  /** Từ khoá phải gõ khớp tuyệt đối, vd "ĐÓNG BĂNG". */
  confirmKeyword: string
  confirmLabel: string
  onConfirm: () => void
}

/**
 * Bản "Modal xác nhận nguy hiểm" generic theo Component Library §6.5 (1 màn hình,
 * gõ từ khoá để bật nút). Riêng flow Huỷ hợp đồng (brief §5.6c) yêu cầu tách 2 bước
 * (xem hậu quả → gõ xác nhận) — đó là composition riêng ở feature layer, không dùng
 * component này trực tiếp.
 */
function DangerConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmKeyword,
  confirmLabel,
  onConfirm,
}: DangerConfirmDialogProps) {
  const [typed, setTyped] = useState("")

  useEffect(() => {
    if (!open) setTyped("")
  }, [open])

  const isMatch = typed === confirmKeyword

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      size="sm"
      title={title}
      titleClassName="text-danger"
      footer={
        <>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Huỷ
          </Button>
          <Button
            variant="destructive-solid"
            size="sm"
            disabled={!isMatch}
            onClick={() => {
              onConfirm()
              onOpenChange(false)
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="mb-4">{description}</div>
      <label className="mb-1.5 block text-[13px] text-text-secondary">
        Gõ lại <span className="font-mono text-text-primary">{confirmKeyword}</span> để xác nhận
      </label>
      <input
        value={typed}
        onChange={(event) => setTyped(event.target.value)}
        placeholder="Nhập từ khoá xác nhận"
        className="w-full rounded-sm border-[0.5px] border-border px-3 py-2.5 font-mono text-sm text-text-primary placeholder:text-text-muted focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
      />
    </Modal>
  )
}

export { DangerConfirmDialog }
