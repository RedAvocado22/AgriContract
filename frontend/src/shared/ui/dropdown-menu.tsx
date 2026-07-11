import type { ReactNode } from "react"
import { Menu as MenuPrimitive } from "@base-ui/react/menu"

import { cn } from "@/shared/lib/utils"

interface DropdownMenuItem {
  key: string
  label: ReactNode
  icon?: string
  danger?: boolean
  onSelect?: () => void
}

interface DropdownMenuProps {
  /** aria-label cho nút trigger — luôn bắt buộc, nút chỉ có icon. */
  triggerLabel: string
  /** Material Symbols ligature name. Mặc định "more_horiz". */
  triggerIcon?: string
  items: DropdownMenuItem[]
}

/**
 * Dùng cho hành động phụ trên hàng bảng (brief §6.5). Tự vẽ nút trigger (style
 * giống IconButton) thay vì nhận component nút từ ngoài — Menu.Trigger tự render
 * ra <button>, nhét thêm 1 <button> khác vào làm children sẽ bị lồng button.
 */
function DropdownMenu({ triggerLabel, triggerIcon = "more_horiz", items }: DropdownMenuProps) {
  return (
    <MenuPrimitive.Root>
      <MenuPrimitive.Trigger
        aria-label={triggerLabel}
        className="inline-flex size-9 items-center justify-center rounded-sm border-[0.5px] border-border bg-surface text-text-secondary transition-colors hover:bg-surface-muted"
      >
        <span className="ms text-lg">{triggerIcon}</span>
      </MenuPrimitive.Trigger>
      <MenuPrimitive.Portal>
        <MenuPrimitive.Positioner sideOffset={4} align="end" className="z-50">
          <MenuPrimitive.Popup className="min-w-40 overflow-hidden rounded-sm border-[0.5px] border-border bg-surface py-1 shadow-lg">
            {items.map((item) => (
              <MenuPrimitive.Item
                key={item.key}
                onClick={item.onSelect}
                className={cn(
                  "flex cursor-pointer items-center gap-2 px-3 py-2 text-sm data-[highlighted]:bg-surface-muted",
                  item.danger ? "text-danger" : "text-text-primary"
                )}
              >
                {item.icon ? <span className="ms text-base">{item.icon}</span> : null}
                {item.label}
              </MenuPrimitive.Item>
            ))}
          </MenuPrimitive.Popup>
        </MenuPrimitive.Positioner>
      </MenuPrimitive.Portal>
    </MenuPrimitive.Root>
  )
}

export { DropdownMenu }
export type { DropdownMenuItem }
