import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface DataTableColumn<T> {
  key: string
  header: string
  /** Đơn vị fr cho grid-template-columns, vd "1.2fr". Mặc định "1fr". */
  width?: string
  align?: "left" | "right"
  render: (row: T) => ReactNode
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  /** Vằn xen kẽ hàng lẻ. Ledger table thường tắt (mockup vẫn để mặc định bật). */
  zebra?: boolean
  className?: string
}

/**
 * Dùng chung cho Data table lẫn Ledger table — khác biệt duy nhất giữa 2 loại
 * là bộ columns caller truyền vào (ledger không có cột thao tác), không cần 2 component.
 */
function DataTable<T>({ columns, rows, rowKey, zebra = true, className }: DataTableProps<T>) {
  const gridTemplateColumns = columns.map((column) => column.width ?? "1fr").join(" ")

  return (
    <div
      data-slot="data-table"
      className={cn("overflow-hidden rounded-md border-[0.5px] border-border bg-surface", className)}
    >
      <div
        className="grid items-center bg-surface-muted px-5 py-2.5 text-[13px] text-text-secondary"
        style={{ gridTemplateColumns }}
      >
        {columns.map((column) => (
          <div key={column.key} className={column.align === "right" ? "text-right" : undefined}>
            {column.header}
          </div>
        ))}
      </div>
      {rows.map((row, index) => (
        <div
          key={rowKey(row)}
          className={cn(
            "grid items-center border-t-[0.5px] border-border px-5 py-3 text-sm",
            zebra && index % 2 === 1 && "bg-page-bg"
          )}
          style={{ gridTemplateColumns }}
        >
          {columns.map((column) => (
            <div key={column.key} className={column.align === "right" ? "text-right" : undefined}>
              {column.render(row)}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

export { DataTable }
export type { DataTableColumn }
