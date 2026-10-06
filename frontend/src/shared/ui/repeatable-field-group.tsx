import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface RepeatableFieldGroupItem {
  key: string
  label: ReactNode
}

interface RepeatableFieldGroupProps {
  items: RepeatableFieldGroupItem[]
  onRemove: (key: string) => void
  onAdd: () => void
  addLabel?: string
  className?: string
}

function RepeatableFieldGroup({
  items,
  onRemove,
  onAdd,
  addLabel = "+ Thêm cột mốc",
  className,
}: RepeatableFieldGroupProps) {
  return (
    <div className={cn("flex flex-col gap-2 rounded-sm border-[0.5px] border-border p-3", className)}>
      {items.map((item) => (
        <div key={item.key} className="flex items-center gap-2">
          <div className="flex-1 rounded-sm border-[0.5px] border-border px-2.5 py-2 text-sm text-text-primary">
            {item.label}
          </div>
          <button
            type="button"
            onClick={() => onRemove(item.key)}
            aria-label="Xoá"
            className="ms cursor-pointer text-lg text-text-muted hover:text-danger"
          >
            close
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={onAdd}
        className="text-left text-[13px] font-medium text-primary"
      >
        {addLabel}
      </button>
    </div>
  )
}

export { RepeatableFieldGroup }
export type { RepeatableFieldGroupItem }
