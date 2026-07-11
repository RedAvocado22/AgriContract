import type { ReactNode } from "react"
import { Select as SelectPrimitive } from "@base-ui/react/select"

import { cn } from "@/shared/lib/utils"

interface SelectOption {
  value: string
  label: string
}

interface SelectDropdownProps {
  id?: string
  label?: ReactNode
  required?: boolean
  placeholder?: string
  options: SelectOption[]
  value: string | null
  onChange: (value: string | null) => void
  error?: string
  disabled?: boolean
  className?: string
}

function SelectDropdown({
  id,
  label,
  required,
  placeholder = "Chọn...",
  options,
  value,
  onChange,
  error,
  disabled,
  className,
}: SelectDropdownProps) {
  return (
    <div className={cn("flex flex-col", className)}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 text-[13px] text-text-primary">
          {label} {required ? <span className="text-danger">*</span> : null}
        </label>
      ) : null}
      <SelectPrimitive.Root
        value={value}
        onValueChange={onChange}
        disabled={disabled}
        items={options.map((option) => ({ value: option.value, label: option.label }))}
      >
        <SelectPrimitive.Trigger
          id={id}
          className={cn(
            "flex items-center justify-between rounded-sm border-[0.5px] bg-surface px-3 py-2.5 text-sm text-text-primary data-[popup-open]:border-primary data-[popup-open]:ring-2 data-[popup-open]:ring-primary/20",
            error ? "border-danger" : "border-border"
          )}
        >
          <SelectPrimitive.Value placeholder={placeholder} className="text-text-primary data-[placeholder]:text-text-muted" />
          <SelectPrimitive.Icon className="ms text-lg text-text-muted">
            keyboard_arrow_down
          </SelectPrimitive.Icon>
        </SelectPrimitive.Trigger>
        <SelectPrimitive.Portal>
          <SelectPrimitive.Positioner sideOffset={4} alignItemWithTrigger={false} className="z-50">
            <SelectPrimitive.Popup className="min-w-(--anchor-width) overflow-hidden rounded-sm border-[0.5px] border-border bg-surface py-1 shadow-lg">
              <SelectPrimitive.List>
                {options.map((option) => (
                  <SelectPrimitive.Item
                    key={option.value}
                    value={option.value}
                    className="flex cursor-pointer items-center justify-between px-3 py-2 text-sm text-text-primary data-[highlighted]:bg-surface-muted"
                  >
                    <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
                    <SelectPrimitive.ItemIndicator className="ms text-base text-primary">
                      check
                    </SelectPrimitive.ItemIndicator>
                  </SelectPrimitive.Item>
                ))}
              </SelectPrimitive.List>
            </SelectPrimitive.Popup>
          </SelectPrimitive.Positioner>
        </SelectPrimitive.Portal>
      </SelectPrimitive.Root>
      {error ? <div className="mt-1 text-[13px] text-danger">{error}</div> : null}
    </div>
  )
}

export { SelectDropdown }
export type { SelectOption }
