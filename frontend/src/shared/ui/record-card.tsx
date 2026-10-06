import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface RecordCardRow {
  key?: string
  label: string
  value: ReactNode
}

interface RecordCardProps {
  /** Mã HĐ/mã bút toán mono, góc trên trái. */
  code?: ReactNode
  /** StatusBadge, góc trên phải. */
  badge?: ReactNode
  /** Số tiền lớn nổi bật (dùng cho ledger card) — nằm dưới header, trên rows. */
  highlight?: ReactNode
  /** Cặp nhãn–giá trị (dùng cho data-table card). */
  rows?: RecordCardRow[]
  /** Dòng phụ 1 dòng (dùng cho master-detail row, thay cho rows). */
  subtitle?: ReactNode
  /** Icon checkbox tự vẽ ở feature layer (dùng cho bulk-select). */
  checkbox?: ReactNode
  selected?: boolean
  /** Hiện chevron_right — báo đây là hàng chạm để mở chi tiết (master-detail). */
  chevron?: boolean
  onClick?: () => void
  className?: string
}

/**
 * Gộp 3 pattern mobile (DataTable→cards, LedgerTable→cards, master-detail row)
 * vào 1 component data-driven — khác biệt chỉ ở props truyền vào, không cần 3 file.
 */
function RecordCard({
  code,
  badge,
  highlight,
  rows,
  subtitle,
  checkbox,
  selected,
  chevron,
  onClick,
  className,
}: RecordCardProps) {
  return (
    <div
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick}
      className={cn(
        "flex w-full items-start gap-3 rounded-[14px] border-[0.5px] bg-surface px-3.5 py-3.5 text-left",
        selected ? "border-[1.5px] border-primary" : "border-border",
        className
      )}
    >
      {checkbox}
      <div className="min-w-0 flex-1">
        {code || badge ? (
          <div
            className={cn(
              "flex items-center justify-between gap-2",
              (rows?.length || highlight || subtitle) && "mb-3"
            )}
          >
            {code ? (
              <span className="truncate font-mono text-sm font-medium text-text-primary">{code}</span>
            ) : null}
            {badge}
          </div>
        ) : null}
        {highlight ? <div className="mb-3">{highlight}</div> : null}
        {subtitle ? <div className="truncate text-[13px] text-text-secondary">{subtitle}</div> : null}
        {rows?.map((row, index) => (
          <div
            key={row.key ?? row.label}
            className={cn(
              "flex items-center justify-between py-1.5 text-sm",
              index > 0 && "border-t-[0.5px] border-surface-muted"
            )}
          >
            <span className="text-text-secondary">{row.label}</span>
            <span className="text-text-primary">{row.value}</span>
          </div>
        ))}
      </div>
      {chevron ? (
        <span className="ms mt-0.5 shrink-0 text-2xl text-border-strong">chevron_right</span>
      ) : null}
    </div>
  )
}

export { RecordCard }
export type { RecordCardRow }
